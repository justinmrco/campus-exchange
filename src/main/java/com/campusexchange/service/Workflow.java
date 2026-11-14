package com.campusexchange.service;
import com.campusexchange.dao.*;
import com.campusexchange.entity.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.sql.*;
@Component
public class Workflow {
 @Autowired private DataSource dataSource;
 @Autowired private ListingDAO listings;
 @Autowired private HandoverLedgerDAO ledgers;
 @FunctionalInterface public interface Work<T>{T run(Connection c)throws SQLException;}
 public <T>T transaction(Work<T> work)throws SQLException{
  try(Connection c=dataSource.getConnection()){
   c.setAutoCommit(false);
   try{T result=work.run(c);c.commit();return result;}
   catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
  }
 }
 public static int update(Connection c,String sql,Object... args)throws SQLException{
  try(PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p.executeUpdate();}
 }
 public static void active(Connection c,int id)throws SQLException{
  try(PreparedStatement p=c.prepareStatement("SELECT active FROM users WHERE user_id=? FOR SHARE")){p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new IllegalStateException("This account is unavailable.");}}
 }
 public HandoverLedger lock(Connection c,int id,int actor)throws SQLException{
  HandoverLedger snapshot=ledgers.findById(id,c,false);
  if(snapshot==null)throw new IllegalArgumentException("Transaction not found.");
  Listing listing=listings.lockForUpdate(snapshot.getListingId(),c);
  HandoverLedger ledger=ledgers.findById(id,c,true);
  if(listing==null||ledger==null)throw new IllegalStateException("Listing or ledger unavailable.");
  if(actor>0){if(actor!=ledger.getBuyerId()&&actor!=ledger.getSellerId())throw new IllegalArgumentException("You are not part of this transaction.");active(c,actor);}
  if("CANCELLED".equals(ledger.getTransactionState()))throw new IllegalStateException("This reservation was already cancelled.");
  String expected=switch(ledger.getTransactionState()){case "RESERVED","MEETUP_PROPOSED","MEETUP_ARRANGED","VERIFYING" -> "RESERVED";case "COMPLETED" -> "COMPLETED";case "DISPUTED" -> listing.getStatus().equals("INACTIVE")?"INACTIVE":"DISPUTED";default -> null;};
  if(expected==null||!expected.equals(listing.getStatus()))throw new IllegalStateException("Listing and ledger disagree; contact an administrator.");
  return ledger;
 }
 public static void state(HandoverLedger ledger,String... allowed){for(String s:allowed)if(s.equals(ledger.getTransactionState()))return;throw new IllegalStateException("Transaction changed. Review its current status.");}
 public static void version(HandoverLedger ledger,long version){if(ledger.getVersion()!=version)throw new IllegalStateException("The proposal changed. Review the latest details before continuing.");}
 public static void listingState(Connection c,int listing,String state)throws SQLException{if(update(c,"UPDATE listings SET status=? WHERE listing_id=?",state,listing)!=1)throw new SQLException("Missing listing");}
 public static void ledgerState(Connection c,int ledger,String state)throws SQLException{if(update(c,"UPDATE handover_ledger SET transaction_state=?,version=version+1 WHERE ledger_id=?",state,ledger)!=1)throw new SQLException("Missing ledger");}
}
