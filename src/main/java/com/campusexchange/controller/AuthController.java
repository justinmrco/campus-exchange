package com.campusexchange.controller;

import com.campusexchange.entity.User;
import com.campusexchange.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;
    @Autowired private com.campusexchange.service.UserEventService userEventService;


    @GetMapping("/")
    public String home() {
        return "redirect:/marketplace";
    }


    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        if (error != null) model.addAttribute("error", resolveLoginError(error));
        return "auth/login";
    }

    @PostMapping("/login")
    public String loginSubmit(@RequestParam String username,
                               @RequestParam String password,
                               HttpServletRequest request,
                               HttpSession session,
                               RedirectAttributes ra) {
        try {
            User user = authService.login(username, password);
            if (user == null) {
                ra.addFlashAttribute("username",username);
                ra.addFlashAttribute("error", "Invalid username or password.");
                return "redirect:/login";
            }

            session.invalidate();
            HttpSession newSession = request.getSession(true);
            newSession.setAttribute("userId", user.getUserId());

            return user.isAdmin() ? "redirect:/admin/dashboard" : "redirect:/marketplace";
        } catch (SQLException e) {
            ra.addFlashAttribute("error", "A system error occurred. Please try again.");
            return "redirect:/login";
        }
    }


    @PostMapping("/logout")
    public String logout(HttpSession session, HttpServletRequest request, RedirectAttributes ra) {
        Object oldId=session.getAttribute("userId");if(oldId instanceof Integer)userEventService.disconnect((Integer)oldId);
        session.invalidate();
        ra.addFlashAttribute("message", "You have been signed out.");
        return "redirect:/login";
    }


    @GetMapping("/register")
    public String registerPage() {
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerSubmit(@RequestParam String username,
                                  @RequestParam String password,
                                  @RequestParam String confirmPassword,
                                  HttpServletRequest request,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        try {
            User user = authService.register(username, password, confirmPassword);

            session.invalidate();
            HttpSession newSession = request.getSession(true);
            newSession.setAttribute("userId", user.getUserId());

            return "redirect:/marketplace?message=welcome";
        } catch (IllegalArgumentException e) {
            String errorMsg = switch (e.getMessage()) {
                case "USERNAME_TAKEN"   -> "That username is already taken. Please choose another.";
                case "VALIDATION_ERROR" -> "Invalid input. Username must be 3–50 characters and password 8?128 characters.";
                default                 -> "Registration failed. Please try again.";
            };
            ra.addFlashAttribute("username",username);
            ra.addFlashAttribute("error", errorMsg);
            return "redirect:/register";
        } catch (SQLException e) {
            ra.addFlashAttribute("error", "A system error occurred. Please try again.");
            return "redirect:/register";
        }
    }

    private String resolveLoginError(String error) {
        return switch (error) {
            case "session" -> "Your session has expired. Please log in again.";
            case "unauthorized" -> "You do not have permission to access that page.";
            default -> "Please log in to continue.";
        };
    }
}
