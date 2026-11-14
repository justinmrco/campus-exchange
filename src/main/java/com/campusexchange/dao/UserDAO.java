package com.campusexchange.dao;

import com.campusexchange.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class UserDAO {

    @Autowired
    private DataSource dataSource;

    public int insert(User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRole());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert user, no generated key.");
    }

    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, role, active, created_at FROM users WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public User findById(int userId) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, role, active, created_at FROM users WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT user_id, username, password_hash, role, active, created_at FROM users ORDER BY created_at DESC,user_id DESC" + com.campusexchange.util.Pagination.limit();
        List<User> users = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) users.add(mapRow(rs));
        }
        return users;
    }

    public boolean existsByUsername(String username) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public void deleteById(int userId) throws SQLException {
        String sql = "UPDATE users SET active=FALSE WHERE user_id=? AND role <> 'ADMIN'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if(ps.executeUpdate()!=1)throw new SQLException("Account cannot be deactivated");
        }
    }

    public void upgradePassword(int id,String previous,String updated) throws SQLException {
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("UPDATE users SET password_hash=? WHERE user_id=? AND password_hash=? AND active=TRUE")){p.setString(1,updated);p.setInt(2,id);p.setString(3,previous);p.executeUpdate();}
    }
    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getBoolean("active"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) u.setCreatedAt(ts.toLocalDateTime());
        return u;
    }
}
