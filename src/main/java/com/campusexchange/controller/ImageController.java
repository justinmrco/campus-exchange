package com.campusexchange.controller;
import com.campusexchange.service.ImageStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.nio.file.Files;
@Controller
public class ImageController {
 private final ImageStore images;
 public ImageController(ImageStore images){this.images=images;}
 @GetMapping("/media/listings/{name}")
 public ResponseEntity<FileSystemResource> image(@PathVariable String name){
  try{var file=images.resolve(name);if(!Files.isRegularFile(file)||Files.isSymbolicLink(file))return ResponseEntity.notFound().build();return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(new FileSystemResource(file));}
  catch(IllegalArgumentException error){return ResponseEntity.notFound().build();}
 }
}
