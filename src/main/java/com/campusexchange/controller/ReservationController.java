package com.campusexchange.controller;

import com.campusexchange.entity.HandoverLedger;
import com.campusexchange.entity.User;
import com.campusexchange.service.ReservationService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;

@Controller
@RequestMapping("/reservation")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @PostMapping("/reserve/{listingId}")
    public String reserve(@PathVariable int listingId,
                           HttpSession session, HttpServletRequest request,
                           RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            HandoverLedger ledger = reservationService.reserve(listingId, user);
            ra.addFlashAttribute("message",
                "Item reserved successfully! Coordinate the meetup in your transactions.");
            return "redirect:/handover/ledger/" + ledger.getLedgerId();
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/listing/" + listingId;
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/listing/" + listingId;
        } catch (SQLException e) {
            ra.addFlashAttribute("error", "A system error occurred. Please try again.");
            return "redirect:/listing/" + listingId;
        }
    }
}
