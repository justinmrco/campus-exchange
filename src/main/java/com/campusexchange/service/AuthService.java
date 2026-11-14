package com.campusexchange.service;

import com.campusexchange.dao.UserDAO;
import com.campusexchange.entity.User;
import com.campusexchange.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
public class AuthService {

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private PasswordUtil passwordUtil;

    public User register(String username, String password, String confirmPassword) throws SQLException {
        username = com.campusexchange.util.Validation.text(username,3,50,"Username");
        if (username.isBlank())
            throw new IllegalArgumentException("VALIDATION_ERROR");
        if (username.length() < 3 || username.length() > 50)
            throw new IllegalArgumentException("VALIDATION_ERROR");
        if (password == null || password.length() < 8 || password.length() > 128)
            throw new IllegalArgumentException("VALIDATION_ERROR");
        if (!password.equals(confirmPassword))
            throw new IllegalArgumentException("VALIDATION_ERROR");
        if (userDAO.existsByUsername(username))
            throw new IllegalArgumentException("USERNAME_TAKEN");

        User user = new User();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordUtil.hash(password));
        user.setRole("USER");

        int id = userDAO.insert(user);
        user.setUserId(id);
        user.setPasswordHash(null);
        return user;
    }

    public int countUsers() throws SQLException {
        return userDAO.countAll();
    }

    public List<User> getAllUsers() throws SQLException {
        return userDAO.findAll();
    }

    public void deleteUser(int userId) throws SQLException {
        userDAO.deleteById(userId);
    }

    public User login(String username, String password) throws SQLException {
        if (username == null || username.length()>50 || password == null || password.length()>128) return null;
        User user = userDAO.findByUsername(username.trim());
        if (user == null || !user.isActive()) return null;
        if (!passwordUtil.verify(password, user.getPasswordHash())) return null;
        if(!user.getPasswordHash().startsWith("pbkdf2:"))userDAO.upgradePassword(user.getUserId(),user.getPasswordHash(),passwordUtil.hash(password));
        user.setPasswordHash(null);
        return user;
    }
}
