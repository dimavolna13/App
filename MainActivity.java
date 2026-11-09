package com.example.csvcleaner;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final int OPEN = 100, SAVE = 101;
    private TextView status;
    private CheckBox trim, blank, duplicates;
    private byte[] output;
    private String originalName = "cleaned.csv";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 36, 24, 24);
        root.setBackgroundColor(Color.rgb(249, 250, 252));
        TextView title = new TextView(this);
        title.setText("CSV Cleaner"); title.setTextSize(28); title.setTextColor(Color.rgb(20, 35, 55));
        root.addView(title);
        TextView desc = new TextView(this);
        desc.setText("Clean CSV files on your phone. No uploads, no account.");
        desc.setTextSize(15); desc.setPadding(0, 12, 0, 24);
        root.addView(desc);
        trim = new CheckBox(this); trim.setText("Trim spaces in cells"); trim.setChecked(true); root.addView(trim);
        blank = new CheckBox(this); blank.setText("Remove empty rows"); blank.setChecked(true); root.addView(blank);
        duplicates = new CheckBox(this); duplicates.setText("Remove duplicate rows (keep first)"); duplicates.setChecked(true); root.addView(duplicates);
        Button open = new Button(this); open.setText("Choose CSV file"); root.addView(open);
        Button save = new Button(this); save.setText("Save cleaned CSV"); save.setEnabled(false); root.addView(save);
        status = new TextView(this); status.setText("Select a UTF-8 CSV file to begin.");
        status.setTextSize(15); status.setPadding(0, 24, 0, 0); root.addView(status);
        setContentView(root);
        open.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream"});
            startActivityForResult(intent, OPEN);
        });
        save.setOnClickListener(v -> {
            if (output == null) return;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("text/csv"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, "cleaned_" + originalName);
            startActivityForResult(intent, SAVE);
        });
        status.setTag(save);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == OPEN) {
            try {
                byte[] raw;
                try (InputStream in = getContentResolver().openInputStream(uri)) { raw = readLimited(in, 5 * 1024 * 1024); }
                String csv = new String(raw, StandardCharsets.UTF_8);
                if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
                if (csv.indexOf('\uFFFD') >= 0) throw new IOException("File may not be UTF-8 encoded.");
                List<List<String>> rows = parseCsv(csv);
                List<List<String>> cleaned = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                int removedBlank = 0, removedDuplicate = 0;
                for (List<String> row : rows) {
                    List<String> copy = new ArrayList<>();
                    for (String cell : row) copy.add(trim.isChecked() ? cell.trim() : cell);
                    if (blank.isChecked() && copy.stream().allMatch(String::isEmpty)) { removedBlank++; continue; }
                    String key = encodeRow(copy);
                    if (duplicates.isChecked() && !seen.add(key)) { removedDuplicate++; continue; }
                    cleaned.add(copy);
                }
                output = encodeCsv(cleaned).getBytes(StandardCharsets.UTF_8);
                originalName = "file.csv";
                if (uri.getLastPathSegment() != null) {
                    String segment = uri.getLastPathSegment();
                    int pos = segment.lastIndexOf('/');
                    originalName = segment.substring(pos + 1).replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (!originalName.toLowerCase(Locale.ROOT).endsWith(".csv")) originalName += ".csv";
                }
                ((Button)status.getTag()).setEnabled(true);
                status.setText("Ready! Rows: " + rows.size() + " → " + cleaned.size() + "\nEmpty rows removed: " + removedBlank + "\nDuplicate rows removed: " + removedDuplicate + "\nTap Save cleaned CSV.");
            } catch (Exception e) { output = null; ((Button)status.getTag()).setEnabled(false); status.setText("Could not process CSV: " + e.getMessage()); }
        } else if (request == SAVE && output != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Cannot open destination");
                out.write(output);
                status.setText("Saved successfully.\n" + status.getText());
            } catch (Exception e) { status.setText("Save failed: " + e.getMessage()); }
        }
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        if (in == null) throw new IOException("File unavailable");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) != -1) { if (out.size() + n > max) throw new IOException("Maximum file size is 5 MB"); out.write(buf, 0, n); }
        return out.toByteArray();
    }

    private static List<List<String>> parseCsv(String text) throws IOException {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder(); boolean quoted = false, afterQuote = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') { if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else { quoted = false; afterQuote = true; } }
                else cell.append(c);
            } else if (afterQuote) {
                if (c == ',') { row.add(cell.toString()); cell.setLength(0); afterQuote = false; }
                else if (c == '\n' || c == '\r') { row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>(); afterQuote = false; if (c == '\r' && i+1 < text.length() && text.charAt(i+1) == '\n') i++; }
                else if (c != ' ' && c != '\t') throw new IOException("Unexpected character after quoted value");
            } else if (c == '"') {
                if (cell.length() != 0) throw new IOException("Unexpected quote in unquoted value");
                quoted = true;
            } else if (c == ',') { row.add(cell.toString()); cell.setLength(0); }
            else if (c == '\n' || c == '\r') {
                row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>();
                if (c == '\r' && i+1 < text.length() && text.charAt(i+1) == '\n') i++;
            } else cell.append(c);
        }
        if (quoted) throw new IOException("Unclosed quoted field");
        if (!row.isEmpty() || cell.length() > 0 || afterQuote) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    private static String encodeRow(List<String> row) { return encodeCsv(Collections.singletonList(row)); }
    private static String encodeCsv(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) sb.append(',');
                String value = row.get(i);
                if (value.contains(",") || value.contains("\n") || value.contains("\r") || value.contains("\"")) sb.append('"').append(value.replace("\"", "\"\"")).append('"');
                else sb.append(value);
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }
}
