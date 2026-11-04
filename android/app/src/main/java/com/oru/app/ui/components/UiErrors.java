package com.oru.app.ui.components;

import android.content.Context;

import com.oru.app.R;
import com.oru.app.data.model.ApiResponse;

/** Turns a failed request into something true to show the user. */
public final class UiErrors {

    private UiErrors() {
    }

    public static String message(Context context, Throwable error) {
        if (error instanceof ApiResponse.ApiException) {
            String message = error.getMessage();
            if (message != null && !message.isEmpty()) {
                return message;
            }
        }
        // Only transport problems reach this point as a bare IOException now.
        if (error instanceof java.io.IOException) {
            return context.getString(R.string.error_network);
        }
        String message = error != null ? error.getMessage() : null;
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return context.getString(R.string.error_unknown);
    }
}
