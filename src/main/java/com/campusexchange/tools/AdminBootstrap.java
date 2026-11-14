package com.campusexchange.tools;
import com.campusexchange.CampusExchangeApplication;
import com.campusexchange.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Arrays;
/** Explicit, one-time command; never promotes an existing account. */
public final class AdminBootstrap {
 public static void main(String[] args)throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("Supply one new administrator username.");
  String username=Validation.text(args[0],3,50,"Username");
  char[] input=System.console()!=null?System.console().readPassword("New administrator password: "):null;
  String password=input!=null?new String(input):System.getenv("CE_ADMIN_PASSWORD");
  if(input!=null)Arrays.fill(input,'\0');
  if(password==null||password.length()<8||password.length()>128)throw new IllegalArgumentException("Provide an 8-128 character password through the console or CE_ADMIN_PASSWORD.");
  String hash=new PasswordUtil().hash(password);
  SpringApplication app=new SpringApplication(CampusExchangeApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
  try(var context=app.run();Connection c=context.getBean(DataSource.class).getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO users(username,password_hash,role) VALUES(?,?,'ADMIN')")){
   p.setString(1,username);p.setString(2,hash);p.executeUpdate();System.out.println("Administrator created. No existing accounts were changed.");
  }
 }
}
