package com.campusexchange.controller;

import com.campusexchange.entity.Dispute;
import com.campusexchange.entity.User;
import com.campusexchange.service.DisputeService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.util.List;

@Controller
@RequestMapping("/dispute")
public class DisputeController {

    @Autowired
    private DisputeService disputeService;
    @Autowired private com.campusexchange.service.HandoverService handovers;


    @GetMapping("/my-disputes")
    public String myDisputes(Model model, HttpSession session, HttpServletRequest request) {
        User user = (User) request.getAttribute("loginUser");
        try {
            List<Dispute> disputes = disputeService.getMyDisputes(user.getUserId());
            model.addAttribute("disputes", disputes);
            model.addAttribute("loginUser", user);
        } catch (SQLException e) {
            model.addAttribute("error", "Could not load disputes.");
        }
        return "dispute/my-disputes";
    }


    @GetMapping("/file/{ledgerId}")
    public String fileDisputePage(@PathVariable int ledgerId, Model model, HttpSession session, HttpServletRequest request) {
        try {var l=handovers.getLedgerById(ledgerId);User u=(User)request.getAttribute("loginUser");if(l==null||(u.getUserId()!=l.getBuyerId()&&u.getUserId()!=l.getSellerId())||java.util.Set.of("COMPLETED","CANCELLED","DISPUTED").contains(l.getTransactionState()))return "redirect:/handover/transactions?error=unavailable";}catch(SQLException e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Transaction unavailable");}
        model.addAttribute("ledgerId", ledgerId);
        model.addAttribute("loginUser", request.getAttribute("loginUser"));
        return "dispute/file";
    }

    @PostMapping("/file/{ledgerId}")
    public String fileDisputeSubmit(@PathVariable int ledgerId,
                                     @RequestParam String reason,
                                     HttpSession session, HttpServletRequest request,
                                     RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            Dispute dispute = disputeService.fileDispute(ledgerId, user.getUserId(), reason);
            ra.addFlashAttribute("message",
                "Dispute filed. An admin will review your case. Dispute ID: " + dispute.getDisputeId());
            return "redirect:/dispute/my-disputes";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("reason",reason);
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/dispute/file/" + ledgerId;
        } catch (SQLException e) {
            ra.addFlashAttribute("reason",reason);
            ra.addFlashAttribute("error", "System error.");
            return "redirect:/dispute/file/" + ledgerId;
        }
    }
}
