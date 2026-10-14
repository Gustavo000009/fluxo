package br.com.fluxo.auth;
import br.com.fluxo.service.RegraNegocioException;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
public final class FotoPerfil {
    private FotoPerfil() {}
    public static String processar(MultipartFile arquivo) {
        if(arquivo.isEmpty() || arquivo.getSize()>2*1024*1024) throw new RegraNegocioException("Escolha uma foto JPG ou PNG de até 2 MB.");
        try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(arquivo.getBytes()))) {
            var leitores=ImageIO.getImageReaders(input);
            if(!leitores.hasNext()) throw new IllegalArgumentException();
            var reader=leitores.next();
            try {
                String formato=reader.getFormatName();
                if(!formato.equalsIgnoreCase("JPEG") && !formato.equalsIgnoreCase("PNG")) throw new IllegalArgumentException();
                reader.setInput(input);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1 || h<1 || w>4096 || h>4096) throw new IllegalArgumentException();
                var origem=reader.read(0);
                var destino=new BufferedImage(256,256,BufferedImage.TYPE_INT_RGB);
                var g=destino.createGraphics();
                try {
                    g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,256,256);
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int lado=Math.min(w,h);g.drawImage(origem,0,0,256,256,(w-lado)/2,(h-lado)/2,(w+lado)/2,(h+lado)/2,null);
                } finally { g.dispose(); }
                var output=new ByteArrayOutputStream();ImageIO.write(destino,"jpg",output);
                return "data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(output.toByteArray());
            } finally { reader.dispose(); }
        } catch(Exception e) { throw new RegraNegocioException("Use uma foto JPG ou PNG válida, de até 2 MB e 4096 pixels por lado."); }
    }
}
