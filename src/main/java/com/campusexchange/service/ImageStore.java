package com.campusexchange.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
public class ImageStore {
    public static final long MAX_BYTES=5L*1024*1024;
    private final Path directory;
    public ImageStore(@Value("${app.upload-directory:uploads/listings}") String directory){this.directory=Path.of(directory).toAbsolutePath().normalize();}
    public Path resolve(String name){
        if(name==null||!name.matches("[a-f0-9-]{36}\\.jpg"))throw new IllegalArgumentException("Invalid image reference.");
        return directory.resolve(name);
    }
    public String save(MultipartFile file){
        if(file==null||file.isEmpty())return null;
        if(file.getSize()>MAX_BYTES)throw new IllegalArgumentException("Photo must be 5 MB or smaller.");
        Path output=null;
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))){
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new IllegalArgumentException("Choose a valid JPEG or PNG photo.");
            var reader=readers.next();
            try{
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("JPEG")&&!format.equalsIgnoreCase("PNG"))throw new IllegalArgumentException("Only JPEG and PNG photos are supported.");
                reader.setInput(stream,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>6000||h>6000||(long)w*h>20_000_000)throw new IllegalArgumentException("Photo exceeds the 20 megapixel or 6,000 pixel limit.");
                BufferedImage source=reader.read(0);
                double scale=Math.min(1,1600.0/Math.max(w,h));
                BufferedImage normalized=new BufferedImage(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=normalized.createGraphics();
                try{g.setColor(Color.WHITE);g.fillRect(0,0,normalized.getWidth(),normalized.getHeight());g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(source,0,0,normalized.getWidth(),normalized.getHeight(),null);}finally{g.dispose();source.flush();}
                Files.createDirectories(directory);
                output=resolve(UUID.randomUUID()+".jpg");
                try(var out=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW)){if(!ImageIO.write(normalized,"jpg",out))throw new IOException("JPEG writer unavailable");}finally{normalized.flush();}
                return "/media/listings/"+output.getFileName();
            }finally{reader.dispose();}
        }catch(IOException error){if(output!=null)try{Files.deleteIfExists(output);}catch(IOException cleanup){error.addSuppressed(cleanup);}throw new IllegalArgumentException("Could not read or save the photo. Choose a valid JPEG or PNG and retry.",error);}
    }
    public void delete(String reference)throws IOException{
        if(reference!=null&&reference.startsWith("/media/listings/"))Files.deleteIfExists(resolve(reference.substring("/media/listings/".length())));
    }
}
