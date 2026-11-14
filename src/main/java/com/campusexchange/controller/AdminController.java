package com.campusexchange.controller;

import com.campusexchange.entity.Dispute;
import com.campusexchange.entity.Listing;
import com.campusexchange.entity.User;
import com.campusexchange.service.AuthService;
import com.campusexchange.service.UserEventService;
import com.campusexchange.service.GlobalEventService;
import com.campusexchange.service.DisputeService;
import com.campusexchange.service.HandoverService;
import com.campusexchange.service.ListingService;
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
@RequestMapping("/admin")
public class AdminController {

    @Autowired private UserEventService userEvents;
    @Autowired private GlobalEventService globalEvents;
    @Autowired private AuthService    authService;
    @Autowired private ListingService listingService;
    @Autowired private HandoverService handoverService;
    @Autowired private DisputeService  disputeService;


    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("loginUser", request.getAttribute("loginUser"));
        try {
            model.addAttribute("userCount",       authService.countUsers());
            model.addAttribute("listingCount",    listingService.countListings());
            model.addAttribute("openDisputes",    disputeService.countOpenDisputes());
            model.addAttribute("allTransactions", handoverService.countTransactions());
        } catch (SQLException e) {
            model.addAttribute("error", "Could not load dashboard stats.");
        }
        return "admin/dashboard";
    }


    @GetMapping("/users")
    public String userList(Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("loginUser", request.getAttribute("loginUser"));
        try {
            List<User> users = authService.getAllUsers();
            model.addAttribute("users", users);
        } catch (SQLException e) {
            model.addAttribute("error", "Could not load users.");
        }
        return "admin/users";
    }

    @PostMapping("/users/{id}/deactivate")
    public String deleteUser(@PathVariable int id, HttpSession session, HttpServletRequest request, RedirectAttributes ra) {
        User admin = (User) request.getAttribute("loginUser");
        if (admin.getUserId() == id) {
            ra.addFlashAttribute("error", "You cannot deactivate your own admin account.");
            return "redirect:/admin/users";
        }
        try {
            authService.deleteUser(id);
            userEvents.disconnect(id);globalEvents.publish("listing-status-change","{}");
            ra.addFlashAttribute("message", "User deactivated.");
        } catch (SQLException e) {
            ra.addFlashAttribute("error", "Could not deactivate account (administrators are protected).");
        }
        return "redirect:/admin/users";
    }


    @GetMapping("/listings")
    public String listingList(Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("loginUser", request.getAttribute("loginUser"));
        try {
            List<Listing> listings = listingService.getAllListings();
            model.addAttribute("listings", listings);
        } catch (SQLException e) {
            model.addAttribute("error", "Could not load listings.");
        }
        return "admin/listings";
    }


    @GetMapping("/transactions")
    public String allTransactions(Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("loginUser", request.getAttribute("loginUser"));
        try {
            model.addAttribute("ledgers", handoverService.getAllTransactions());
        } catch (SQLException e) {
            model.addAttribute("error", "Could not load transactions.");
        }
        return "admin/transactions";
    }


    @GetMapping("/disputes")
    public String disputeList(Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("loginUser", request.getAttribute("loginUser"));
        try {
            model.addAttribute("disputes", disputeService.getAllDisputes());
        } catch (SQLException e) {
            model.addAttribute("error", "Could not load disputes.");
        }
        return "admin/disputes";
    }

    @GetMapping("/disputes/{id}")
    public String disputeDetail(@PathVariable int id, Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("loginUser", request.getAttribute("loginUser"));
        try {
            Dispute d = disputeService.getDisputeById(id);
            if (d == null) return "redirect:/admin/disputes";
            model.addAttribute("dispute", d);
        } catch (SQLException e) {
            return "redirect:/admin/disputes";
        }
        return "admin/dispute-detail";
    }

    @PostMapping("/disputes/{id}/resolve")
    public String resolveDispute(@PathVariable int id,
                                  @RequestParam String adminNotes, HttpServletRequest request,
                                  RedirectAttributes ra) {
        try {
            disputeService.resolveDispute(id, ((User)request.getAttribute("loginUser")).getUserId(), adminNotes);
            ra.addFlashAttribute("message", "Dispute #" + id + " resolved.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("adminNotes",adminNotes);
            ra.addFlashAttribute("error", e.getMessage());
        } catch (SQLException e) {
            ra.addFlashAttribute("adminNotes",adminNotes);
            ra.addFlashAttribute("error", "System error.");
        }
        return "redirect:/admin/disputes/"+id;
    }
}
