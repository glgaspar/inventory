package com.pessimaideia.inventory.data;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.pessimaideia.inventory.model.SessionItem;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SessionStore {

    private static final String TAG = "Inventory";
    private static final String FILE_NAME = "session.json";
    private static final Type LIST_TYPE = new TypeToken<List<SessionItem>>() {}.getType();

    private final File file;
    private final Gson gson = new Gson();

    public SessionStore(File directory) {
        this.file = new File(directory, FILE_NAME);
    }

    public synchronized boolean hasSession() {
        return file.exists() && file.length() > 0;
    }

    /** Loads the session, or an empty list if there is none or the file is unreadable. */
    public synchronized List<SessionItem> load() {
        if (!hasSession()) return new ArrayList<>();
        try (Reader reader = new FileReader(file)) {
            List<SessionItem> items = gson.fromJson(reader, LIST_TYPE);
            return items != null ? items : new ArrayList<>();
        } catch (IOException | JsonParseException e) {
            Log.w(TAG, "Could not read session file, starting empty", e);
            return new ArrayList<>();
        }
    }

    /** Writes the whole session. An empty list removes the file. */
    public synchronized void save(List<SessionItem> items) {
        if (items.isEmpty()) {
            clear();
            return;
        }
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(items, LIST_TYPE, writer);
        } catch (IOException e) {
            Log.e(TAG, "Could not save session", e);
        }
    }

    public synchronized void clear() {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Could not delete session file");
        }
    }
}