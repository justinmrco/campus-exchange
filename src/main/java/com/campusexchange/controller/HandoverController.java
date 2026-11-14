package com.campusexchange.controller;

import com.campusexchange.entity.HandoverLedger;
import com.campusexchange.entity.User;
import com.campusexchange.service.HandoverService;
import com.campusexchange.service.LedgerEventService;
import com.campusexchange.service.ReservationService;
import com.campusexchange.util.SseUtil;
import com.campusexchange.util.TokenUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/handover")
public class HandoverController {

    private static final Logger log = LoggerFactory.getLogger(HandoverController.class);

    @Autowired
    private HandoverService handoverService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private LedgerEventService ledgerEventService;

    @Autowired
    private TokenUtil tokenUtil;
    @Autowired private com.campusexchange.dao.DisputeDAO disputes;


    @GetMapping("/transactions")
    public String myTransactions(Model model, HttpSession session, HttpServletRequest request) {
        User user = (User) request.getAttribute("loginUser");
        try {
            List<HandoverLedger> ledgers = handoverService.getMyTransactions(user.getUserId());
            model.addAttribute("ledgers", ledgers);
            model.addAttribute("loginUser", user);
        } catch (SQLException e) {
            log.error("Failed to load transactions for user {}", user.getUserId(), e);
            model.addAttribute("error", "Could not load transactions.");
        }
        return "handover/transactions";
    }


    @GetMapping("/ledger/{id}")
    public String ledgerDetail(@PathVariable int id,
                                @RequestParam(required = false) String message,
                                @RequestParam(required = false) String error,
                                Model model, HttpSession session, HttpServletRequest request) {
        User user = (User) request.getAttribute("loginUser");
        try {
            HandoverLedger ledger = handoverService.getLedgerById(id);
            if (ledger == null) return "redirect:/handover/transactions";
            if (ledger.getBuyerId() != user.getUserId() && ledger.getSellerId() != user.getUserId()
                    && !user.isAdmin())
                return "redirect:/handover/transactions?error=unauthorized";

            model.addAttribute("ledger", ledger);
            model.addAttribute("participant",ledger.getBuyerId()==user.getUserId()||ledger.getSellerId()==user.getUserId());
            model.addAttribute("dispute",disputes.findByLedgerId(id));
            model.addAttribute("loginUser", user);
            if (message != null) model.addAttribute("message", message);
            if (error != null) model.addAttribute("error", error);

            // Determine role — needed in all states (MEETUP_PROPOSED uses otherUsername)
            boolean isBuyer = ledger.getBuyerId() == user.getUserId();
            model.addAttribute("isBuyer", isBuyer);
            model.addAttribute("otherUsername", isBuyer ? ledger.getSellerUsername() : ledger.getBuyerUsername());

            // Token display only once meetup is confirmed
            if ((ledger.getBuyerId()==user.getUserId() || ledger.getSellerId()==user.getUserId()) && ledger.getBuyerToken() != null && ledger.getSellerToken() != null) {
                String myToken = isBuyer ? ledger.getBuyerToken() : ledger.getSellerToken();
                model.addAttribute("myDisplayCode", tokenUtil.toDisplayCode(myToken));
                model.addAttribute("myVerified",   isBuyer ? ledger.isBuyerVerified()  : ledger.isSellerVerified());
                model.addAttribute("otherVerified", isBuyer ? ledger.isSellerVerified() : ledger.isBuyerVerified());
            }
        } catch (SQLException e) {
            return "redirect:/handover/transactions";
        }
        return "handover/ledger-detail";
    }


    @GetMapping(value = "/ledger/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ledgerEvents(@PathVariable int id, HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        SseUtil.setHeaders(response);
        User user = (User) request.getAttribute("loginUser");
        if (user == null) return SseUtil.closedEmitter();
        try {
            HandoverLedger ledger = handoverService.getLedgerById(id);
            if (ledger == null
                    || (ledger.getBuyerId() != user.getUserId()
                        && ledger.getSellerId() != user.getUserId()
                        && !user.isAdmin())) {
                return SseUtil.closedEmitter();
            }
        } catch (SQLException e) {
            return SseUtil.closedEmitter();
        }
        SseEmitter emitter=ledgerEventService.subscribe(id,user.getUserId(),session);
        try { var current=handoverService.getLedgerById(id); if(current!=null)emitter.send(SseEmitter.event().name("snapshot").data(java.util.Map.of("version",current.getVersion()))); }
        catch(Exception error){emitter.complete();}
        return emitter;
    }


    @PostMapping("/propose/{ledgerId}")
    public String proposeMeetup(@PathVariable int ledgerId,
                                 @RequestParam String location,
                                 @RequestParam String meetupTime,
                                 @RequestParam long version,
                                 HttpSession session, HttpServletRequest request,
                                 RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            handoverService.proposeMeetup(ledgerId, user.getUserId(), location, meetupTime, version);
            ra.addFlashAttribute("message", "Meetup proposed! Waiting for the other party to accept or counter-propose.");
            return "redirect:/handover/ledger/" + ledgerId;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("draft",java.util.Map.of("location",location,"meetupTime",meetupTime));
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/handover/ledger/" + ledgerId;
        } catch (SQLException e) {
            ra.addFlashAttribute("draft",java.util.Map.of("location",location,"meetupTime",meetupTime));
            ra.addFlashAttribute("error", "System error. Please try again.");
            return "redirect:/handover/ledger/" + ledgerId;
        }
    }


    @PostMapping("/accept/{ledgerId}")
    public String acceptMeetup(@PathVariable int ledgerId, @RequestParam long version,
                                HttpSession session, HttpServletRequest request,
                                RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            handoverService.acceptMeetup(ledgerId, user.getUserId(), version);
            ra.addFlashAttribute("message", "Meetup confirmed! Your verification code is ready.");
            return "redirect:/handover/ledger/" + ledgerId;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/handover/ledger/" + ledgerId;
        } catch (SQLException e) {
            ra.addFlashAttribute("error", "System error. Please try again.");
            return "redirect:/handover/ledger/" + ledgerId;
        }
    }


    @PostMapping("/cancel/{ledgerId}")
    public String cancelReservation(@PathVariable int ledgerId,
                                     HttpSession session, HttpServletRequest request,
                                     RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            reservationService.cancelReservation(ledgerId, user.getUserId());
            ra.addFlashAttribute("message", "Reservation cancelled. The listing is now available again.");
            return "redirect:/handover/transactions";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/handover/ledger/" + ledgerId;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "System error. Please try again.");
            return "redirect:/handover/ledger/" + ledgerId;
        }
    }


    @PostMapping("/verify/{ledgerId}")
    public String verifyHandover(@PathVariable int ledgerId,
                                  @RequestParam String submittedCode,
                                  HttpSession session, HttpServletRequest request,
                                  RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            String result = handoverService.submitMyCode(ledgerId, user.getUserId(), submittedCode);
            if ("COMPLETED".equals(result)) {
                ra.addFlashAttribute("message", "COMPLETED");
            } else {
                ra.addFlashAttribute("message", "WAITING");
            }
            return "redirect:/handover/ledger/" + ledgerId;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/handover/ledger/" + ledgerId;
        } catch (SQLException e) {
            ra.addFlashAttribute("error", "System error. Please try again.");
            return "redirect:/handover/ledger/" + ledgerId;
        }
    }
}
