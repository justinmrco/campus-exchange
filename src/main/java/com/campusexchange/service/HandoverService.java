package com.campusexchange.service;
import com.campusexchange.dao.*;
import com.campusexchange.entity.*;
import com.campusexchange.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.List;
@Service
public class HandoverService {
 @Autowired private Workflow workflow;
 @Autowired private HandoverLedgerDAO handoverLedgerDAO;
 @Autowired private TokenUtil tokenUtil;
 @Autowired private LedgerEventService ledgerEventService;
 @Autowired private UserEventService userEventService;
 @Autowired private GlobalEventService globalEventService;
 private void changed(HandoverLedger l){ledgerEventService.publishReload(l.getLedgerId());userEventService.publish(l.getBuyerId(),"transaction-changed","{}");userEventService.publish(l.getSellerId(),"transaction-changed","{}");}
 public void proposeMeetup(int id,int user,String location,String time,long version)throws SQLException{
  String loc=Validation.text(location,1,200,"Location");Timestamp when=Timestamp.valueOf(Validation.future(time));
  HandoverLedger ledger=workflow.transaction(c->{HandoverLedger l=workflow.lock(c,id,user);Workflow.state(l,"RESERVED","MEETUP_PROPOSED");Workflow.version(l,version);if(Workflow.update(c,"UPDATE handover_ledger SET meetup_location=?,meetup_time=?,proposed_by=?,transaction_state='MEETUP_PROPOSED',version=version+1 WHERE ledger_id=?",loc,when,user,id)!=1)throw new SQLException("Missing ledger");return l;});changed(ledger);
 }
 public void acceptMeetup(int id,int user,long version)throws SQLException{
  HandoverLedger ledger=workflow.transaction(c->{HandoverLedger l=workflow.lock(c,id,user);Workflow.state(l,"MEETUP_PROPOSED");Workflow.version(l,version);if(l.getProposedBy()==null||l.getProposedBy()==user)throw new IllegalStateException("Only the other participant can accept the proposal.");Validation.future(l.getMeetupTime().toString());Workflow.active(c,l.getBuyerId());Workflow.active(c,l.getSellerId());if(Workflow.update(c,"UPDATE handover_ledger SET buyer_token=?,seller_token=?,proposed_by=NULL,transaction_state='MEETUP_ARRANGED',version=version+1 WHERE ledger_id=?",tokenUtil.generateToken(),tokenUtil.generateToken(),id)!=1)throw new SQLException("Missing ledger");return l;});changed(ledger);
 }
 public String submitMyCode(int id,int user,String code)throws SQLException{
  HandoverLedger result=workflow.transaction(c->{HandoverLedger l=workflow.lock(c,id,user);Workflow.state(l,"MEETUP_ARRANGED","VERIFYING","COMPLETED");boolean buyer=l.getBuyerId()==user;String expected=buyer?l.getSellerToken():l.getBuyerToken();if(expected==null||code==null||!tokenUtil.toDisplayCode(expected).equalsIgnoreCase(code.trim()))throw new IllegalArgumentException("Enter the other participant's 8-character code.");if("COMPLETED".equals(l.getTransactionState()))return l;if(l.getMeetupTime()==null||java.time.LocalDateTime.now(Validation.ZONE).isAfter(l.getMeetupTime().plusDays(1)))throw new IllegalStateException("Confirmation codes expired. File a dispute for assistance.");Workflow.active(c,l.getBuyerId());Workflow.active(c,l.getSellerId());boolean already=buyer?l.isBuyerVerified():l.isSellerVerified();if(!already){Workflow.update(c,"UPDATE handover_ledger SET "+(buyer?"buyer_verified":"seller_verified")+"=TRUE,transaction_state='VERIFYING',version=version+1 WHERE ledger_id=?",id);if(buyer)l.setBuyerVerified(true);else l.setSellerVerified(true);}if(l.isBuyerVerified()&&l.isSellerVerified()){Workflow.ledgerState(c,id,"COMPLETED");Workflow.listingState(c,l.getListingId(),"COMPLETED");l.setTransactionState("COMPLETED");}return l;});changed(result);if(result.isCompleted())globalEventService.publish("listing-status-change","{}");return result.isCompleted()?"COMPLETED":"WAITING";
 }
    public HandoverLedger getLedgerById(int ledgerId) throws SQLException {
        return handoverLedgerDAO.findById(ledgerId);
    }

    public HandoverLedger getLedgerByListingId(int listingId) throws SQLException {
        return handoverLedgerDAO.findByListingId(listingId);
    }

    public List<HandoverLedger> getMyTransactions(int userId) throws SQLException {
        return handoverLedgerDAO.findByUserId(userId);
    }

    public int countTransactions() throws SQLException {
        return handoverLedgerDAO.countAll();
    }

    public List<HandoverLedger> getAllTransactions() throws SQLException {
        return handoverLedgerDAO.findAll();
    }

}
