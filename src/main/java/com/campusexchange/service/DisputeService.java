package com.campusexchange.service;
import com.campusexchange.dao.*;
import com.campusexchange.entity.*;
import com.campusexchange.util.Validation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.List;
@Service
public class DisputeService {
 @Autowired private DisputeDAO disputeDAO;
 @Autowired private Workflow workflow;
 @Autowired private UserEventService userEventService;
 @Autowired private LedgerEventService ledgerEventService;
 @Autowired private GlobalEventService globalEventService;
 private void changed(HandoverLedger l){ledgerEventService.publishReload(l.getLedgerId());userEventService.publish(l.getBuyerId(),"transaction-changed","{}");userEventService.publish(l.getSellerId(),"transaction-changed","{}");globalEventService.publish("listing-status-change","{}");}
 public Dispute fileDispute(int id,int user,String reason)throws SQLException{
  String text=Validation.text(reason,1,5000,"Reason");
  HandoverLedger[] ledger=new HandoverLedger[1];
  Dispute result=workflow.transaction(c->{HandoverLedger l=workflow.lock(c,id,user);ledger[0]=l;Workflow.state(l,"RESERVED","MEETUP_PROPOSED","MEETUP_ARRANGED","VERIFYING");Dispute d=new Dispute();d.setLedgerId(id);d.setReporterId(user);d.setReason(text);d.setStatus("OPEN");d.setDisputeId(disputeDAO.insert(d,c));Workflow.ledgerState(c,id,"DISPUTED");Workflow.listingState(c,l.getListingId(),"DISPUTED");return d;});changed(ledger[0]);return result;
 }
 public void resolveDispute(int id,int admin,String notes)throws SQLException{
  String text=Validation.text(notes,1,5000,"Resolution notes");Dispute snapshot=disputeDAO.findById(id);if(snapshot==null)throw new IllegalArgumentException("Dispute not found.");
  HandoverLedger ledger=workflow.transaction(c->{Workflow.active(c,admin);try(PreparedStatement p=c.prepareStatement("SELECT role FROM users WHERE user_id=?")){p.setInt(1,admin);try(ResultSet r=p.executeQuery()){if(!r.next()||!"ADMIN".equals(r.getString(1)))throw new IllegalStateException("Administrator access required.");}}HandoverLedger l=workflow.lock(c,snapshot.getLedgerId(),0);Workflow.state(l,"DISPUTED");try(PreparedStatement p=c.prepareStatement("SELECT status FROM disputes WHERE dispute_id=? FOR UPDATE")){p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(!r.next()||!"OPEN".equals(r.getString(1)))throw new IllegalStateException("Dispute already resolved.");}}if(Workflow.update(c,"UPDATE disputes SET status='RESOLVED',admin_notes=?,resolved_at=NOW() WHERE dispute_id=? AND status='OPEN'",text,id)!=1)throw new SQLException("Resolution failed");Workflow.listingState(c,l.getListingId(),"INACTIVE");Workflow.update(c,"UPDATE handover_ledger SET version=version+1 WHERE ledger_id=?",l.getLedgerId());return l;});changed(ledger);
 }
    public Dispute getDisputeById(int disputeId) throws SQLException {
        return disputeDAO.findById(disputeId);
    }

    public List<Dispute> getAllDisputes() throws SQLException {
        return disputeDAO.findAll();
    }

    public int countOpenDisputes() throws SQLException {
        return disputeDAO.countOpen();
    }

    public List<Dispute> getOpenDisputes() throws SQLException {
        return disputeDAO.findOpen();
    }

    public List<Dispute> getMyDisputes(int userId) throws SQLException {
        return disputeDAO.findByUserId(userId);
    }
}
