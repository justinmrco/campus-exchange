package com.campusexchange.dao;

import com.campusexchange.entity.Dispute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DisputeDAO {

    @Autowired
    private DataSource dataSource;

    /**
     * Insert dispute within an existing transaction (caller manages connection).
     */
    public int insert(Dispute dispute, Connection conn) throws SQLException {
        String sql = "INSERT INTO disputes (ledger_id, reporter_id, reason, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, dispute.getLedgerId());
            ps.setInt(2, dispute.getReporterId());
            ps.setString(3, dispute.getReason());
            ps.setString(4, Dispute.STATUS_OPEN);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert dispute.");
    }

    public Dispute findByLedgerId(int ledger)throws SQLException{try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(buildJoinSql()+" WHERE d.ledger_id=?")){p.setInt(1,ledger);try(ResultSet r=p.executeQuery()){return r.next()?mapRow(r):null;}}}
    public Dispute findById(int disputeId) throws SQLException {
        String sql = buildJoinSql() + " WHERE d.dispute_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, disputeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Dispute> findAll() throws SQLException {
        String sql = buildJoinSql() + " ORDER BY d.created_at DESC,d.dispute_id DESC" + com.campusexchange.util.Pagination.limit();
        List<Dispute> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public List<Dispute> findOpen() throws SQLException {
        String sql = buildJoinSql() + " WHERE d.status = 'OPEN' ORDER BY d.created_at DESC,d.dispute_id DESC" + com.campusexchange.util.Pagination.limit();
        List<Dispute> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public List<Dispute> findByUserId(int userId) throws SQLException {
        String sql = buildJoinSql() + " WHERE (hl.buyer_id = ? OR hl.seller_id = ?) ORDER BY d.created_at DESC,d.dispute_id DESC" + com.campusexchange.util.Pagination.limit();
        List<Dispute> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int countOpen() throws SQLException {
        String sql = "SELECT COUNT(*) FROM disputes WHERE status = 'OPEN'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String buildJoinSql() {
        return "SELECT d.dispute_id, d.ledger_id, d.reporter_id, d.reason, d.status, " +
               "d.admin_notes, d.created_at, d.resolved_at, " +
               "u.username AS reporter_username, " +
               "l.title AS listing_title, l.listing_id " +
               "FROM disputes d " +
               "JOIN users u ON d.reporter_id = u.user_id " +
               "JOIN handover_ledger hl ON d.ledger_id = hl.ledger_id " +
               "JOIN listings l ON hl.listing_id = l.listing_id";
    }

    private Dispute mapRow(ResultSet rs) throws SQLException {
        Dispute d = new Dispute();
        d.setDisputeId(rs.getInt("dispute_id"));
        d.setLedgerId(rs.getInt("ledger_id"));
        d.setReporterId(rs.getInt("reporter_id"));
        d.setReporterUsername(rs.getString("reporter_username"));
        d.setReason(rs.getString("reason"));
        d.setStatus(rs.getString("status"));
        d.setAdminNotes(rs.getString("admin_notes"));
        d.setListingTitle(rs.getString("listing_title"));
        d.setListingId(rs.getInt("listing_id"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) d.setCreatedAt(ca.toLocalDateTime());
        Timestamp ra = rs.getTimestamp("resolved_at");
        if (ra != null) d.setResolvedAt(ra.toLocalDateTime());
        return d;
    }
}
