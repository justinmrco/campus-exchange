package com.campusexchange.service;
import com.campusexchange.dao.*;
import com.campusexchange.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.SQLException;
@Service
public class ReservationService {
 @Autowired private Workflow workflow;
 @Autowired private ListingDAO listingDAO;
 @Autowired private HandoverLedgerDAO handoverLedgerDAO;
 @Autowired private UserEventService userEventService;
 @Autowired private GlobalEventService globalEventService;
 @Autowired private LedgerEventService ledgerEventService;
 public void cancelReservation(int id,int user)throws SQLException{
  HandoverLedger ledger=workflow.transaction(c->{HandoverLedger l=workflow.lock(c,id,user);Workflow.state(l,"RESERVED","MEETUP_PROPOSED");Workflow.listingState(c,l.getListingId(),"AVAILABLE");Workflow.ledgerState(c,id,"CANCELLED");return l;});
  globalEventService.publish("listing-status-change","{}");ledgerEventService.publishReload(id);
  userEventService.publish(ledger.getBuyerId(),"transaction-changed","{}");userEventService.publish(ledger.getSellerId(),"transaction-changed","{}");
 }
 public HandoverLedger reserve(int id,User buyer)throws SQLException{
  HandoverLedger result=workflow.transaction(c->{Listing listing=listingDAO.lockForUpdate(id,c);if(listing==null)throw new IllegalArgumentException("Listing not found.");if(!listing.isAvailable())throw new IllegalStateException("This item is no longer available.");if(listing.getSellerId()==buyer.getUserId())throw new IllegalArgumentException("You cannot reserve your own listing.");Workflow.active(c,buyer.getUserId());Workflow.active(c,listing.getSellerId());Workflow.listingState(c,id,"RESERVED");HandoverLedger l=new HandoverLedger();l.setListingId(id);l.setBuyerId(buyer.getUserId());l.setSellerId(listing.getSellerId());l.setTransactionState("RESERVED");l.setLedgerId(handoverLedgerDAO.insertWithinTransaction(l,c));return l;});
  userEventService.publish(result.getSellerId(),"item-reserved","{}");globalEventService.publish("listing-status-change","{}");return result;
 }
}
