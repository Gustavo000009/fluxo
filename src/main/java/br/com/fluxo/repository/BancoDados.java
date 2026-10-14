package br.com.fluxo.repository;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Banco local transacional. Nunca é exposto por HTTP. */
@Component @EnableScheduling
public class BancoDados {
    private final String url;
    private final Path pasta;
    private Connection ancora;
    private final String usuario, senha;
    private final boolean postgres;
    public BancoDados(String contas) { this(contas, "", "", "", false); }
    @org.springframework.beans.factory.annotation.Autowired
    public BancoDados(@Value("${app.contas:dados/contas.json}") String contas,
            @Value("${app.database.url:}") String databaseUrl,
            @Value("${app.database.usuario:}") String usuario,
            @Value("${app.database.senha:}") String senha,
            @Value("${app.database.obrigatorio:false}") boolean obrigatorio) {
        postgres = !databaseUrl.isBlank();
        if(obrigatorio && !postgres) throw new IllegalArgumentException("Configure o banco remoto antes de iniciar na nuvem.");
        if(postgres && (!databaseUrl.startsWith("jdbc:postgresql://") || !databaseUrl.contains("sslmode=verify-full")))
            throw new IllegalArgumentException("Banco remoto requer JDBC PostgreSQL com sslmode=verify-full.");
        this.usuario = postgres ? usuario : "sa";
        this.senha = postgres ? senha : "";
        try {
            pasta = Path.of(contas).toAbsolutePath().getParent();
            if(!postgres) Files.createDirectories(pasta);
            url = postgres ? databaseUrl : "jdbc:h2:file:" + pasta.resolve("fluxo-db").toString().replace('\\','/');
            if(!postgres) ancora = conectar();
            try (var c = conectar(); var s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS registros (dono VARCHAR(100) NOT NULL, chave VARCHAR(100) NOT NULL, conteudo TEXT NOT NULL, PRIMARY KEY(dono,chave))");
            }
            backup();
        } catch (Exception e) { throw new IllegalStateException("Não foi possível abrir o banco de dados.", e); }
    }
    @jakarta.annotation.PreDestroy
    public synchronized void fechar() throws SQLException { if(ancora!=null) ancora.close(); }
    private Connection conectar() throws SQLException {
        var propriedades = new Properties();
        propriedades.setProperty("user",usuario); propriedades.setProperty("password",senha);
        if(postgres) {
            propriedades.setProperty("sslfactory","org.postgresql.ssl.DefaultJavaSSLFactory");
            propriedades.setProperty("connectTimeout","10");
            propriedades.setProperty("socketTimeout","30");
        }
        return DriverManager.getConnection(url, propriedades);
    }
    public synchronized String ler(String dono, String chave) {
        try (var c = conectar(); var s = c.prepareStatement("SELECT conteudo FROM registros WHERE dono=? AND chave=?")) {
            s.setString(1,dono); s.setString(2,chave);
            try (var r=s.executeQuery()) { return r.next()?r.getString(1):null; }
        } catch(SQLException e) { throw new IllegalStateException("Não foi possível ler os dados.",e); }
    }
    public synchronized SortedMap<String,String> listar(String dono) {
        var itens=new TreeMap<String,String>();
        try (var c=conectar(); var s=c.prepareStatement("SELECT chave,conteudo FROM registros WHERE dono=? ORDER BY chave")) {
            s.setString(1,dono);
            try(var r=s.executeQuery()) { while(r.next()) itens.put(r.getString(1),r.getString(2)); }
            return itens;
        } catch(SQLException e) { throw new IllegalStateException("Não foi possível ler o histórico.",e); }
    }
    public synchronized void salvar(String dono, Map<String,String> itens) {
        if(ler("sistema","excluida-"+dono)!=null)throw new IllegalStateException("Conta excluída.");
        try(var c=conectar()) {
            c.setAutoCommit(false);
            try(var s=c.prepareStatement(upsert())) {
                for(var item:itens.entrySet()) { s.setString(1,dono); s.setString(2,item.getKey()); s.setString(3,item.getValue()); s.addBatch(); }
                s.executeBatch(); c.commit();
            } catch(Exception e) { c.rollback(); throw e; }
        } catch(Exception e) { throw new IllegalStateException("Não foi possível salvar os dados.",e); }
    }
    public synchronized void excluirConta(String dono, Map<String,String> sistema, Set<String> remover) {
        try(var c=conectar()) {
            c.setAutoCommit(false);
            try {
                try(var s=c.prepareStatement("DELETE FROM registros WHERE dono=?")){s.setString(1,dono);s.executeUpdate();}
                try(var s=c.prepareStatement("DELETE FROM registros WHERE dono='sistema' AND chave=?")){
                    for(String chave:remover){s.setString(1,chave);s.addBatch();}s.executeBatch();
                }
                try(var s=c.prepareStatement(upsert())) {
                    for(var item:sistema.entrySet()){s.setString(1,"sistema");s.setString(2,item.getKey());s.setString(3,item.getValue());s.addBatch();}s.executeBatch();
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }catch(Exception e){throw new IllegalStateException("Não foi possível excluir a conta.",e);}
    }
    private String upsert() {
        return postgres ? "INSERT INTO registros (dono,chave,conteudo) VALUES (?,?,?) ON CONFLICT (dono,chave) DO UPDATE SET conteudo=EXCLUDED.conteudo"
            : "MERGE INTO registros (dono,chave,conteudo) KEY(dono,chave) VALUES (?,?,?)";
    }
    @Scheduled(cron="0 0 3 * * *")
    public synchronized void backup() {
        if(postgres) return; // Backups remotos devem ser configurados fora do aplicativo.
        try {
            Path destino=pasta.resolve("backups"); Files.createDirectories(destino);
            Path arquivo=destino.resolve("fluxo-"+LocalDate.now()+".zip");
            if(Files.exists(arquivo)) return;
            try(var c=conectar();var s=c.createStatement()) { s.execute("BACKUP TO '"+arquivo.toString().replace("'","''")+"'"); }
        } catch(Exception e) { throw new IllegalStateException("Não foi possível criar a cópia de segurança.",e); }
    }
}
