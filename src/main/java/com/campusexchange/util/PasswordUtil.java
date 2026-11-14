package com.campusexchange.util;
import org.springframework.stereotype.Component;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
@Component
public class PasswordUtil {
 private static final SecureRandom RANDOM = new SecureRandom();
 private static final int ITERATIONS = 600000;
 public String hash(String plaintext) {
  byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
  return "pbkdf2:"+ITERATIONS+":"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(plaintext,salt,ITERATIONS));
 }
 private byte[] derive(String password,byte[] salt,int iterations) {
  PBEKeySpec key=new PBEKeySpec(password.toCharArray(),salt,iterations,256);
  try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded(); }
  catch(GeneralSecurityException e) { throw new IllegalStateException("Password hashing unavailable",e); }
  finally { key.clearPassword(); }
 }
 public boolean verify(String plaintext,String stored) {
  if(plaintext==null || stored==null || plaintext.length()>128) return false;
  try {
   String[] p=stored.split(":"); byte[] actual,expected;
   if(p.length==4 && p[0].equals("pbkdf2")) {
    int n=Integer.parseInt(p[1]); if(n!=ITERATIONS) return false;
    byte[] salt=Base64.getDecoder().decode(p[2]); if(salt.length!=16) return false;
    actual=derive(plaintext,salt,n); expected=Base64.getDecoder().decode(p[3]);
   } else if(p.length==2) {
    byte[] salt=Base64.getDecoder().decode(p[0]); if(salt.length!=16) return false;
    MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(salt);
    actual=digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));expected=Base64.getDecoder().decode(p[1]);
   } else return false;
   return expected.length==32 && MessageDigest.isEqual(expected,actual);
  }catch(IllegalArgumentException|GeneralSecurityException e){return false;}
 }
}
