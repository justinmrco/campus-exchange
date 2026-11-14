package com.campusexchange.dao;

import com.campusexchange.entity.HandoverLedger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HandoverLedgerDAO {

    @Autowired
    private DataSource dataSource;

    public int insertWithinTransaction(HandoverLedger ledger, Connection conn) throws SQLException {
        String sql = "INSERT INTO handover_ledger (listing_id, buyer_id, seller_id, transaction_state) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, ledger.getListingId());
            ps.setInt(2, ledger.getBuyerId());
            ps.setInt(3, ledger.getSellerId());
            ps.setString(4, HandoverLedger.STATE_RESERVED);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert handover ledger.");
    }

    public HandoverLedger findById(int ledgerId) throws SQLException {
      try(Connection c=dataSource.getConnection()){return findById(ledgerId,c,false);}
    }
    public HandoverLedger findById(int ledgerId, Connection conn, boolean lock) throws SQLException {
        String sql = "SELECT hl.*, " +
                     "b.username AS buyer_username, s.username AS seller_username, " +
                     "l.title AS listing_title " +
                     "FROM handover_ledger hl " +
                     "JOIN users b ON hl.buyer_id = b.user_id " +
                     "JOIN users s ON hl.seller_id = s.user_id " +
                     "JOIN listings l ON hl.listing_id = l.listing_id " +
                     "WHERE hl.ledger_id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ledgerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public HandoverLedger findByListingId(int listingId) throws SQLException {
        String sql = "SELECT hl.*, " +
                     "b.username AS buyer_username, s.username AS seller_username, " +
                     "l.title AS listing_title " +
                     "FROM handover_ledger hl " +
                     "JOIN users b ON hl.buyer_id = b.user_id " +
                     "JOIN users s ON hl.seller_id = s.user_id " +
                     "JOIN listings l ON hl.listing_id = l.listing_id " +
                     "WHERE hl.listing_id = ? ORDER BY hl.created_at DESC,hl.ledger_id DESC LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<HandoverLedger> findByUserId(int userId) throws SQLException {
        String sql = "SELECT hl.*, " +
                     "b.username AS buyer_username, s.username AS seller_username, " +
                     "l.title AS listing_title " +
                     "FROM handover_ledger hl " +
                     "JOIN users b ON hl.buyer_id = b.user_id " +
                     "JOIN users s ON hl.seller_id = s.user_id " +
                     "JOIN listings l ON hl.listing_id = l.listing_id " +
                     "WHERE hl.buyer_id = ? OR hl.seller_id = ? " +
                     "ORDER BY hl.updated_at DESC,hl.ledger_id DESC" + com.campusexchange.util.Pagination.limit();
        List<HandoverLedger> list = new ArrayList<>();
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

    public List<HandoverLedger> findAll() throws SQLException {
        String sql = "SELECT hl.*, " +
                     "b.username AS buyer_username, s.username AS seller_username, " +
                     "l.title AS listing_title " +
                     "FROM handover_ledger hl " +
                     "JOIN users b ON hl.buyer_id = b.user_id " +
                     "JOIN users s ON hl.seller_id = s.user_id " +
                     "JOIN listings l ON hl.listing_id = l.listing_id " +
                     "ORDER BY hl.updated_at DESC,hl.ledger_id DESC" + com.campusexchange.util.Pagination.limit();
        List<HandoverLedger> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM handover_ledger";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }


    private HandoverLedger mapRow(ResultSet rs) throws SQLException {
        HandoverLedger hl = new HandoverLedger();
        hl.setLedgerId(rs.getInt("ledger_id"));
        hl.setVersion(rs.getLong("version"));
        hl.setListingId(rs.getInt("listing_id"));
        hl.setBuyerId(rs.getInt("buyer_id"));
        hl.setSellerId(rs.getInt("seller_id"));
        hl.setBuyerUsername(rs.getString("buyer_username"));
        hl.setSellerUsername(rs.getString("seller_username"));
        hl.setListingTitle(rs.getString("listing_title"));
        hl.setMeetupLocation(rs.getString("meetup_location"));
        Timestamp mt = rs.getTimestamp("meetup_time");
        if (mt != null) hl.setMeetupTime(mt.toLocalDateTime());
        hl.setBuyerToken(rs.getString("buyer_token"));
        hl.setSellerToken(rs.getString("seller_token"));
        hl.setBuyerVerified(rs.getBoolean("buyer_verified"));
        hl.setSellerVerified(rs.getBoolean("seller_verified"));
        int pb = rs.getInt("proposed_by");
        if (!rs.wasNull()) hl.setProposedBy(pb);
        hl.setTransactionState(rs.getString("transaction_state"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) hl.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) hl.setUpdatedAt(ua.toLocalDateTime());
        return hl;
    }
}
