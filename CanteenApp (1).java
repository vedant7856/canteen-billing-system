import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Canteen Token & Billing System
 * Java Swing (UI) + JDBC (MySQL)
 *
 * Tabs:  1) Billing   2) Manage Menu   3) Day-End Sales Summary
 */

/* ===================== DATABASE CONNECTION ===================== */
class Db {
    // >>> CHANGE ONLY THESE TWO LINES <<<
    static final String USER = "root";
    static final String PASS = "your_mysql_password";

    static final String URL =
        "jdbc:mysql://localhost:3306/canteen_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}

/* ===================== SIMPLE BAR CHART ===================== */
class BarChart extends JPanel {
    private Map<String, Double> data = new LinkedHashMap<>();
    private String title = "";

    BarChart() {
        setBackground(Color.WHITE);
    }

    void setData(String title, Map<String, Double> data) {
        this.title = title;
        this.data = data;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.setColor(CanteenApp.DARK);
        g.drawString(title, 20, 28);

        if (data.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            g.setColor(Color.GRAY);
            g.drawString("No sales to show yet", w / 2 - 70, h / 2);
            g.dispose();
            return;
        }

        int left = 65, right = 20, top = 50, bottom = 55;
        int cw = w - left - right, ch = h - top - bottom;
        double max = 1;
        for (double v : data.values()) max = Math.max(max, v);

        // grid lines + y labels
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        for (int i = 0; i <= 4; i++) {
            int y = top + ch - ch * i / 4;
            g.setColor(new Color(225, 225, 225));
            g.drawLine(left, y, left + cw, y);
            g.setColor(Color.GRAY);
            g.drawString(String.valueOf((int) (max * i / 4)), 10, y + 4);
        }

        // bars
        int n = data.size();
        int slot = cw / n;
        int bw = Math.min(60, slot * 2 / 3);
        int i = 0;
        for (Map.Entry<String, Double> e : data.entrySet()) {
            int bh = (int) (ch * e.getValue() / max);
            int x = left + i * slot + (slot - bw) / 2;
            int y = top + ch - bh;

            g.setPaint(new GradientPaint(x, y, CanteenApp.PRIMARY, x, y + Math.max(bh, 1),
                    CanteenApp.PRIMARY.darker()));
            g.fillRoundRect(x, y, bw, Math.max(bh, 2), 8, 8);

            g.setColor(CanteenApp.DARK);
            String val = String.format("%.0f", e.getValue());
            g.drawString(val, x + (bw - g.getFontMetrics().stringWidth(val)) / 2, y - 5);

            String label = e.getKey().length() > 10 ? e.getKey().substring(0, 9) + ".." : e.getKey();
            g.drawString(label, x + (bw - g.getFontMetrics().stringWidth(label)) / 2, top + ch + 20);
            i++;
        }
        g.dispose();
    }
}

/* ===================== MAIN WINDOW ===================== */
public class CanteenApp extends JFrame {

    // ---- colours & fonts ----
    static final Color PRIMARY = new Color(255, 111, 0);
    static final Color DARK    = new Color(38, 50, 56);
    static final Color BG      = new Color(245, 247, 250);
    static final Color GREEN   = new Color(46, 125, 50);
    static final Color RED     = new Color(198, 40, 40);
    static final Font  PLAIN   = new Font("Segoe UI", Font.PLAIN, 14);
    static final Font  BOLD    = new Font("Segoe UI", Font.BOLD, 14);

    // ---- billing tab ----
    DefaultTableModel menuModel = model("ID", "Item", "Category", "Price");
    DefaultTableModel cartModel = model("Item", "Price", "Qty", "Total");
    JTable menuTable = new JTable(menuModel);
    JTable cartTable = new JTable(cartModel);
    JTextField searchField = new JTextField();
    JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
    JLabel totalLabel = new JLabel("Rs. 0.00");
    JLabel tokenLabel = new JLabel("Next Token: -");

    // ---- manage menu tab ----
    DefaultTableModel manageModel = model("ID", "Item", "Category", "Price");
    JTable manageTable = new JTable(manageModel);
    JTextField nameField = new JTextField();
    JTextField priceField = new JTextField();
    JComboBox<String> categoryBox =
            new JComboBox<>(new String[]{"Meals", "Snacks", "Beverages", "Desserts"});

    // ---- summary tab ----
    JLabel ordersLabel = new JLabel("0");
    JLabel revenueLabel = new JLabel("Rs. 0.00");
    JLabel topItemLabel = new JLabel("-");
    JComboBox<String> viewBox =
            new JComboBox<>(new String[]{"Today - Item-wise Sales (Rs.)", "Last 7 Days - Daily Sales (Rs.)"});
    BarChart chart = new BarChart();

    public CanteenApp() {
        setTitle("Canteen Token & Billing System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1120, 700);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 15));
        tabs.addTab("  Billing  ", buildBillingTab());
        tabs.addTab("  Manage Menu  ", buildManageTab());
        tabs.addTab("  Day-End Summary  ", buildSummaryTab());
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 2) refreshSummary();
        });
        add(tabs, BorderLayout.CENTER);

        loadMenus();
        refreshToken();
    }

    /* ---------------------------------------------------------- */
    /*  UI BUILDING                                               */
    /* ---------------------------------------------------------- */
    JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PRIMARY);
        p.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("Canteen Token & Billing System");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);

        JLabel clock = new JLabel();
        clock.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        clock.setForeground(Color.WHITE);
        new javax.swing.Timer(1000, e -> clock.setText(
                new java.text.SimpleDateFormat("dd MMM yyyy  hh:mm:ss a").format(new java.util.Date()))).start();

        p.add(title, BorderLayout.WEST);
        p.add(clock, BorderLayout.EAST);
        return p;
    }

    JPanel buildBillingTab() {
        JPanel root = new JPanel(new GridLayout(1, 2, 15, 0));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(15, 15, 15, 15));

        // ----- left: menu -----
        JPanel left = card("Menu (double-click to add)");
        styleTable(menuTable);

        searchField.setFont(PLAIN);
        searchField.setToolTipText("Search item name");
        searchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                fillMenu(menuModel, searchField.getText().trim());
            }
        });
        menuTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) addToCart();
            }
        });

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.add(new JLabel("Search:"), BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bottom.setOpaque(false);
        qtySpinner.setFont(PLAIN);
        JButton addBtn = button("Add to Cart", PRIMARY);
        addBtn.addActionListener(e -> addToCart());
        bottom.add(new JLabel("Qty:"));
        bottom.add(qtySpinner);
        bottom.add(addBtn);

        left.add(top, BorderLayout.NORTH);
        left.add(new JScrollPane(menuTable), BorderLayout.CENTER);
        left.add(bottom, BorderLayout.SOUTH);

        // ----- right: cart -----
        JPanel right = card("Current Bill");
        styleTable(cartTable);

        tokenLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        tokenLabel.setForeground(PRIMARY);
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        totalLabel.setForeground(DARK);

        JButton removeBtn = button("Remove Item", RED);
        JButton clearBtn = button("Clear", Color.GRAY);
        JButton saveBtn = button("Generate Token & Save", GREEN);
        removeBtn.addActionListener(e -> removeFromCart());
        clearBtn.addActionListener(e -> clearCart());
        saveBtn.addActionListener(e -> saveOrder());

        JPanel info = new JPanel(new BorderLayout());
        info.setOpaque(false);
        info.add(tokenLabel, BorderLayout.WEST);
        info.add(totalLabel, BorderLayout.EAST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btns.setOpaque(false);
        btns.add(removeBtn);
        btns.add(clearBtn);
        btns.add(saveBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(info, BorderLayout.NORTH);
        south.add(btns, BorderLayout.SOUTH);

        right.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        right.add(south, BorderLayout.SOUTH);

        root.add(left);
        root.add(right);
        return root;
    }

    JPanel buildManageTab() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(15, 15, 15, 15));

        styleTable(manageTable);
        manageTable.getSelectionModel().addListSelectionListener(e -> {
            int r = manageTable.getSelectedRow();
            if (r >= 0 && !e.getValueIsAdjusting()) {
                nameField.setText(manageModel.getValueAt(r, 1).toString());
                categoryBox.setSelectedItem(manageModel.getValueAt(r, 2).toString());
                priceField.setText(manageModel.getValueAt(r, 3).toString());
            }
        });

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        form.setBackground(Color.WHITE);
        nameField.setPreferredSize(new Dimension(180, 30));
        priceField.setPreferredSize(new Dimension(90, 30));
        categoryBox.setPreferredSize(new Dimension(130, 30));
        nameField.setFont(PLAIN);
        priceField.setFont(PLAIN);
        categoryBox.setFont(PLAIN);

        JButton add = button("Add", GREEN);
        JButton upd = button("Update", PRIMARY);
        JButton del = button("Delete", RED);
        JButton clr = button("Clear", Color.GRAY);
        add.addActionListener(e -> addMenuItem());
        upd.addActionListener(e -> updateMenuItem());
        del.addActionListener(e -> deleteMenuItem());
        clr.addActionListener(e -> clearForm());

        form.add(new JLabel("Name:"));     form.add(nameField);
        form.add(new JLabel("Category:")); form.add(categoryBox);
        form.add(new JLabel("Price:"));    form.add(priceField);
        form.add(add); form.add(upd); form.add(del); form.add(clr);

        root.add(form, BorderLayout.NORTH);
        root.add(new JScrollPane(manageTable), BorderLayout.CENTER);
        return root;
    }

    JPanel buildSummaryTab() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel stats = new JPanel(new GridLayout(1, 3, 15, 0));
        stats.setOpaque(false);
        stats.add(statCard("Orders Today", ordersLabel));
        stats.add(statCard("Revenue Today", revenueLabel));
        stats.add(statCard("Best Seller", topItemLabel));

        viewBox.setFont(PLAIN);
        JButton refresh = button("Refresh", PRIMARY);
        refresh.addActionListener(e -> refreshSummary());
        viewBox.addActionListener(e -> refreshSummary());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        bar.setOpaque(false);
        bar.add(new JLabel("Chart:"));
        bar.add(viewBox);
        bar.add(refresh);

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(stats, BorderLayout.NORTH);
        north.add(bar, BorderLayout.SOUTH);

        root.add(north, BorderLayout.NORTH);
        root.add(chart, BorderLayout.CENTER);
        return root;
    }

    /* ---------------------------------------------------------- */
    /*  BILLING LOGIC                                             */
    /* ---------------------------------------------------------- */
    void addToCart() {
        int r = menuTable.getSelectedRow();
        if (r < 0) { info("Select a menu item first."); return; }

        String name = menuModel.getValueAt(r, 1).toString();
        double price = ((Number) menuModel.getValueAt(r, 3)).doubleValue();
        int qty = (Integer) qtySpinner.getValue();

        // already in cart -> just increase quantity
        for (int i = 0; i < cartModel.getRowCount(); i++) {
            if (cartModel.getValueAt(i, 0).equals(name)) {
                int newQty = (Integer) cartModel.getValueAt(i, 2) + qty;
                cartModel.setValueAt(newQty, i, 2);
                cartModel.setValueAt(price * newQty, i, 3);
                updateTotal();
                return;
            }
        }
        cartModel.addRow(new Object[]{name, price, qty, price * qty});
        updateTotal();
    }

    void removeFromCart() {
        int r = cartTable.getSelectedRow();
        if (r < 0) { info("Select a cart row to remove."); return; }
        cartModel.removeRow(r);
        updateTotal();
    }

    void clearCart() {
        cartModel.setRowCount(0);
        updateTotal();
    }

    double cartTotal() {
        double t = 0;
        for (int i = 0; i < cartModel.getRowCount(); i++)
            t += ((Number) cartModel.getValueAt(i, 3)).doubleValue();
        return t;
    }

    void updateTotal() {
        totalLabel.setText(String.format("Rs. %.2f", cartTotal()));
    }

    /** Next token for today (1,2,3...). Restarts every day. */
    int nextToken(Connection c, boolean lock) throws SQLException {
        String sql = "SELECT COALESCE(MAX(token_no),0)+1 FROM orders WHERE order_date=CURDATE()"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    void refreshToken() {
        try (Connection c = Db.get()) {
            tokenLabel.setText("Next Token: #" + nextToken(c, false));
        } catch (SQLException e) {
            tokenLabel.setText("Next Token: -");
            error(e);
        }
    }

    void saveOrder() {
        if (cartModel.getRowCount() == 0) { info("Cart is empty."); return; }

        double total = cartTotal();
        int token;

        try (Connection c = Db.get()) {
            c.setAutoCommit(false);                       // save bill + items together
            try {
                token = nextToken(c, true);

                long orderId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO orders(token_no, order_date, total) VALUES(?, CURDATE(), ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, token);
                    ps.setDouble(2, total);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        orderId = keys.getLong(1);
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO order_items(order_id, item_name, price, qty, line_total) VALUES(?,?,?,?,?)")) {
                    for (int i = 0; i < cartModel.getRowCount(); i++) {
                        ps.setLong(1, orderId);
                        ps.setString(2, cartModel.getValueAt(i, 0).toString());
                        ps.setDouble(3, ((Number) cartModel.getValueAt(i, 1)).doubleValue());
                        ps.setInt(4, (Integer) cartModel.getValueAt(i, 2));
                        ps.setDouble(5, ((Number) cartModel.getValueAt(i, 3)).doubleValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            error(e);
            return;
        }

        showReceipt(token, total);
        clearCart();
        refreshToken();
    }

    void showReceipt(int token, double total) {
        StringBuilder sb = new StringBuilder();
        sb.append("      CANTEEN BILL\n");
        sb.append("  Date: ").append(new java.text.SimpleDateFormat("dd-MM-yyyy hh:mm a")
                .format(new java.util.Date())).append("\n");
        sb.append("  TOKEN NO: ").append(token).append("\n");
        sb.append("--------------------------------\n");
        for (int i = 0; i < cartModel.getRowCount(); i++) {
            sb.append(String.format("%-14s %2d x %6.2f\n",
                    cartModel.getValueAt(i, 0),
                    (Integer) cartModel.getValueAt(i, 2),
                    ((Number) cartModel.getValueAt(i, 1)).doubleValue()));
        }
        sb.append("--------------------------------\n");
        sb.append(String.format("TOTAL:            Rs. %.2f\n", total));
        sb.append("\n   Thank you! Visit again.");

        JTextArea area = new JTextArea(sb.toString());
        area.setFont(new Font("Monospaced", Font.BOLD, 14));
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, area, "Token #" + token, JOptionPane.PLAIN_MESSAGE);
    }

    /* ---------------------------------------------------------- */
    /*  MENU MANAGEMENT                                           */
    /* ---------------------------------------------------------- */
    void loadMenus() {
        fillMenu(menuModel, "");
        fillMenu(manageModel, "");
    }

    void fillMenu(DefaultTableModel m, String search) {
        m.setRowCount(0);
        String sql = "SELECT id, name, category, price FROM menu_items WHERE name LIKE ? ORDER BY category, name";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + search + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDouble(4)});
            }
        } catch (SQLException e) {
            error(e);
        }
    }

    void addMenuItem() {
        Double price = readPrice();
        if (price == null) return;
        runUpdate("INSERT INTO menu_items(name, category, price) VALUES(?,?,?)",
                nameField.getText().trim(), categoryBox.getSelectedItem().toString(), price);
    }

    void updateMenuItem() {
        int r = manageTable.getSelectedRow();
        if (r < 0) { info("Select an item in the table first."); return; }
        Double price = readPrice();
        if (price == null) return;
        runUpdate("UPDATE menu_items SET name=?, category=?, price=? WHERE id=?",
                nameField.getText().trim(), categoryBox.getSelectedItem().toString(), price,
                manageModel.getValueAt(r, 0));
    }

    void deleteMenuItem() {
        int r = manageTable.getSelectedRow();
        if (r < 0) { info("Select an item in the table first."); return; }
        if (JOptionPane.showConfirmDialog(this, "Delete this item?", "Confirm",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        runUpdate("DELETE FROM menu_items WHERE id=?", manageModel.getValueAt(r, 0));
    }

    /** Runs any INSERT/UPDATE/DELETE with parameters, then reloads the menu tables. */
    void runUpdate(String sql, Object... params) {
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
            clearForm();
            loadMenus();
        } catch (SQLException e) {
            error(e);
        }
    }

    Double readPrice() {
        if (nameField.getText().trim().isEmpty()) { info("Enter item name."); return null; }
        try {
            double p = Double.parseDouble(priceField.getText().trim());
            if (p <= 0) throw new NumberFormatException();
            return p;
        } catch (NumberFormatException e) {
            info("Enter a valid price greater than 0.");
            return null;
        }
    }

    void clearForm() {
        nameField.setText("");
        priceField.setText("");
        categoryBox.setSelectedIndex(0);
        manageTable.clearSelection();
    }

    /* ---------------------------------------------------------- */
    /*  DAY-END SUMMARY                                           */
    /* ---------------------------------------------------------- */
    void refreshSummary() {
        try (Connection c = Db.get()) {
            // totals for today
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(total),0) FROM orders WHERE order_date=CURDATE()");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                ordersLabel.setText(String.valueOf(rs.getInt(1)));
                revenueLabel.setText(String.format("Rs. %.2f", rs.getDouble(2)));
            }

            // item-wise sales today (also gives best seller)
            Map<String, Double> items = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT oi.item_name, SUM(oi.line_total) FROM order_items oi " +
                    "JOIN orders o ON o.id = oi.order_id WHERE o.order_date=CURDATE() " +
                    "GROUP BY oi.item_name ORDER BY 2 DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.put(rs.getString(1), rs.getDouble(2));
            }
            topItemLabel.setText(items.isEmpty() ? "-" : items.keySet().iterator().next());

            if (viewBox.getSelectedIndex() == 0) {
                chart.setData("Today - Item-wise Sales (Rs.)", items);
            } else {
                Map<String, Double> days = new LinkedHashMap<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT DATE_FORMAT(order_date,'%d %b'), SUM(total) FROM orders " +
                        "WHERE order_date >= CURDATE() - INTERVAL 6 DAY " +
                        "GROUP BY order_date ORDER BY order_date");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) days.put(rs.getString(1), rs.getDouble(2));
                }
                chart.setData("Last 7 Days - Daily Sales (Rs.)", days);
            }
        } catch (SQLException e) {
            error(e);
        }
    }

    /* ---------------------------------------------------------- */
    /*  SMALL HELPERS (styling + messages)                        */
    /* ---------------------------------------------------------- */
    static DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    static void styleTable(JTable t) {
        t.setRowHeight(28);
        t.setFont(PLAIN);
        t.setGridColor(new Color(235, 235, 235));
        t.setSelectionBackground(new Color(255, 224, 178));
        t.setSelectionForeground(Color.BLACK);
        t.getTableHeader().setFont(BOLD);
        t.getTableHeader().setBackground(DARK);
        t.getTableHeader().setForeground(Color.WHITE);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    static JButton button(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(BOLD);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(8, 16, 8, 16));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    static JPanel card(String title) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(PRIMARY, 2), title,
                        javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, BOLD, DARK),
                new EmptyBorder(8, 8, 8, 8)));
        return p;
    }

    static JPanel statCard(String title, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 6, 0, 0, PRIMARY), new EmptyBorder(12, 15, 12, 15)));
        JLabel t = new JLabel(title);
        t.setFont(PLAIN);
        t.setForeground(Color.GRAY);
        value.setFont(new Font("Segoe UI", Font.BOLD, 26));
        value.setForeground(DARK);
        p.add(t, BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    void error(Exception e) {
        JOptionPane.showMessageDialog(this,
                "Database problem:\n" + e.getMessage() +
                "\n\nCheck: MySQL is running, canteen_db.sql was executed, and the password in class Db is correct.",
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    /* ---------------------------------------------------------- */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new CanteenApp().setVisible(true));
    }
}
