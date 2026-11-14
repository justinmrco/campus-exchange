package com.campusexchange.controller;

import com.campusexchange.entity.Listing;
import com.campusexchange.entity.User;
import com.campusexchange.service.GlobalEventService;
import com.campusexchange.service.ListingService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Controller
public class ListingController {

    @Autowired
    private ListingService listingService;

    @Autowired
    private GlobalEventService globalEventService;


    @GetMapping("/marketplace")
    public String marketplace(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) String message,
                               @RequestParam(required = false) String error,
                               Model model, HttpSession session, HttpServletRequest request) {
        try {
            List<Listing> listings = listingService.browseAvailable(keyword, category);
            model.addAttribute("listings", listings);
            model.addAttribute("keyword", keyword);
            model.addAttribute("category", category);
            model.addAttribute("loginUser", request.getAttribute("loginUser"));
            if (message != null) model.addAttribute("message", resolveMessage(message));
            if (error != null) model.addAttribute("error", error);
        } catch (SQLException e) {
            model.addAttribute("error", "Could not load listings.");
        }
        return "listing/marketplace";
    }


    @GetMapping("/listing/{id}")
    public String listingDetail(@PathVariable int id, Model model, HttpSession session, HttpServletRequest request,
                               RedirectAttributes ra) {
        try {
            Listing listing = listingService.getListingById(id);
            if (listing == null) {
                ra.addFlashAttribute("error", "Listing not found.");
                return "redirect:/marketplace";
            }
            model.addAttribute("listing", listing);
            model.addAttribute("loginUser", request.getAttribute("loginUser"));
        } catch (SQLException e) {
            ra.addFlashAttribute("error", "A system error occurred.");
            return "redirect:/marketplace";
        }
        return "listing/detail";
    }


    @GetMapping("/my-listings")
    public String myListings(Model model, HttpSession session, HttpServletRequest request) {
        User user = (User) request.getAttribute("loginUser");
        try {
            List<Listing> listings = listingService.getMyListings(user.getUserId());
            model.addAttribute("listings", listings);
            model.addAttribute("loginUser", user);
        } catch (SQLException e) {
            model.addAttribute("error", "Could not load your listings.");
        }
        return "listing/my-listings";
    }


    @GetMapping("/listing/create")
    public String createListingPage(Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("loginUser", request.getAttribute("loginUser"));
        synchronized(session){if(session.getAttribute("listingToken")==null)session.setAttribute("listingToken",java.util.UUID.randomUUID().toString());}
        model.addAttribute("formToken",session.getAttribute("listingToken"));
        return "listing/create";
    }

    @PostMapping("/listing/create")
    public String createListingSubmit(@RequestParam String title, @RequestParam String formToken,
                                       @RequestParam String description,
                                       @RequestParam String price,
                                       @RequestParam String category,
                                       @RequestParam(required=false) MultipartFile photo,
                                       HttpSession session, HttpServletRequest request,
                                       RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            Listing listing;
            synchronized(session){
              if(formToken.equals(session.getAttribute("lastListingToken")))return "redirect:/listing/"+session.getAttribute("lastListingId");
              if(!formToken.equals(session.getAttribute("listingToken")))throw new IllegalArgumentException("Form expired. Reload before creating a listing.");
              listing=listingService.createListing(user.getUserId(),title,description,price,category,photo);
              session.setAttribute("lastListingToken",formToken);session.setAttribute("lastListingId",listing.getListingId());session.removeAttribute("listingToken");
            }
            ra.addFlashAttribute("message", "Listing \"" + listing.getTitle() + "\" created successfully.");
            globalEventService.publish("listing-created",
                "{}");
            return "redirect:/my-listings";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("draft",java.util.Map.of("title",title,"description",description,"price",price,"category",category));
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/listing/create";
        } catch (SQLException e) {
            ra.addFlashAttribute("draft",java.util.Map.of("title",title,"description",description,"price",price,"category",category));
            ra.addFlashAttribute("error", "System error. Please try again.");
            return "redirect:/listing/create";
        }
    }


    @GetMapping("/listing/{id}/edit")
    public String editListingPage(@PathVariable int id, Model model, HttpSession session, HttpServletRequest request,
                                  RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            Listing listing = listingService.getListingById(id);
            if (listing == null || listing.getSellerId() != user.getUserId()) {
                ra.addFlashAttribute("error", "You cannot access this listing.");
                return "redirect:/my-listings";
            }
            model.addAttribute("listing", listing);
            model.addAttribute("loginUser", user);
        } catch (SQLException e) {
            ra.addFlashAttribute("error", "A system error occurred.");
            return "redirect:/my-listings";
        }
        return "listing/edit";
    }

    @PostMapping("/listing/{id}/edit")
    public String editListingSubmit(@PathVariable int id,
                                     @RequestParam String title,
                                     @RequestParam String description,
                                     @RequestParam String price,
                                     @RequestParam String category,
                                     @RequestParam(required=false) MultipartFile photo,
                                     @RequestParam(defaultValue="false") boolean removePhoto,
                                     HttpSession session, HttpServletRequest request,
                                     RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            listingService.editListing(id, user.getUserId(), title, description, price, category,photo,removePhoto);
            globalEventService.publish("listing-status-change","{}");
            ra.addFlashAttribute("message", "Listing updated.");
            return "redirect:/my-listings";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("draft",java.util.Map.of("title",title,"description",description,"price",price,"category",category));
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/listing/" + id + "/edit";
        } catch (SQLException e) {
            ra.addFlashAttribute("draft",java.util.Map.of("title",title,"description",description,"price",price,"category",category));
            ra.addFlashAttribute("error", "System error.");
            return "redirect:/listing/" + id + "/edit";
        }
    }


    @PostMapping("/listing/{id}/delete")
    public String deleteListing(@PathVariable int id, HttpSession session, HttpServletRequest request, RedirectAttributes ra) {
        User user = (User) request.getAttribute("loginUser");
        try {
            listingService.deleteListing(id, user.getUserId());
            globalEventService.publish("listing-status-change","{}");
            ra.addFlashAttribute("message", "Listing removed.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (SQLException e) {
            ra.addFlashAttribute("error", "System error.");
        }
        return "redirect:/my-listings";
    }

    private String resolveMessage(String msg) {
        return switch (msg) {
            case "welcome" -> "Welcome to CampusExchange!";
            default -> "";
        };
    }
}
