package dev.android.dynamicisland.island.source;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Independently implemented detector for ongoing ride, courier and food-order cards.
 *
 * It only interprets notification text/progress already mirrored by System UI. It does not query
 * apps, launch services or create another notification listener.
 */
final class LiveActivityParser {
    static final class Info {
        final String etaText;
        final float progressRatio;
        final String statusTitle;
        final String subStatusText;
        final int stage;

        Info(String etaText, float progressRatio, String statusTitle, String subStatusText, int stage) {
            this.etaText = etaText;
            this.progressRatio = progressRatio;
            this.statusTitle = statusTitle;
            this.subStatusText = subStatusText;
            this.stage = stage;
        }
    }

    private static final Pattern ETA = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?\\s*"
                    + "(?:mins?|minutes?|minutos?|minuti|minuten|minuut|dakika|dk|menit|phút|"
                    + "分钟|分鐘|分|분|دقيقة|دقائق|मिनट|минут(?:а|ы)?)"
                    + "(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DISTANCE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*(km|kilometers?|kilometres?|miles?|mi)"
                    + "(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PROMOTION = Pattern.compile(
            "(?:\\b(?:promo(?:tion)?|coupon|voucher|discount|cashback|sale|special offer|"
                    + "order now|shop now|use code|promo code|save \\d+|up to \\d+% off)\\b"
                    + "|(?:\\d{1,3}\\s*%\\s*(?:off|discount|cashback)))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String[] TRACKING = {
            "on the way", "en route", "out for delivery", "preparing your order",
            "order is preparing", "order accepted", "order placed", "order confirmed",
            "order in progress", "order status", "ride status", "ride in progress",
            "driver assigned", "driver is coming", "driver on the way", "courier assigned",
            "delivery partner", "your driver", "your courier", "captain assigned",
            "arriving soon", "is arriving", "nearby", "picked up", "heading to you",
            "heading your way", "left the restaurant", "left the store", "ready for pickup",
            "being prepared", "restaurant is preparing", "rider is on the way",
            "подтвержден", "курьер", "водитель", "в пути", "заказ принят", "заказ готов",
            "en camino", "repartidor", "conductor", "pedido confirmado", "pedido en camino",
            "a caminho", "entregador", "pedido confirmado", "en livraison", "livreur",
            "unterwegs", "fahrer", "lieferung", "in arrivo", "autista", "consegna",
            "yolda", "kurye", "sürücü", "sedang menuju", "pengemudi", "penghantar",
            "đang giao", "tài xế", "配送中", "正在配送", "司机", "骑手", "配送員",
            "배달 중", "기사님", "운행 중", "في الطريق", "السائق", "وصل الطلب",
            "डिलीवरी", "रास्ते में"
    };

    private static final String[] EARLY = {
            "placed", "accepted", "booked", "confirmed", "finding a driver",
            "searching for a driver", "looking for a driver", "driver search",
            "finding a courier", "preparing", "being prepared", "restaurant accepted",
            "order received", "assigned"
    };

    private static final String[] FINAL = {
            "arriving", "arrived", "reaching you", "at your location", "nearby",
            "out for delivery", "delivered", "completed", "ready for pickup",
            "ready to collect", "successfully delivered", "trip completed",
            "entregado", "entregue", "consegnato"
    };

    private LiveActivityParser() {}

    static Info parse(StatusBarNotification sbn) {
        if (sbn == null || !NavigationParser.isLiveActivityApp(sbn.getPackageName())) return null;
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return null;
        if ((notification.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) == 0
                && !Notification.CATEGORY_PROGRESS.equals(notification.category)) return null;

        Bundle extras = notification.extras;
        String title = clean(value(extras, Notification.EXTRA_TITLE));
        String body = clean(first(extras, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUMMARY_TEXT, Notification.EXTRA_INFO_TEXT));
        String sub = clean(value(extras, Notification.EXTRA_SUB_TEXT));
        String content = (title + " " + body + " " + sub).trim();
        if (content.isEmpty()) return null;

        String normalized = content.toLowerCase(Locale.ROOT);
        if (PROMOTION.matcher(normalized).find()) return null;

        int progress = extras.getInt(Notification.EXTRA_PROGRESS, -1);
        int max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
        boolean actualProgress = max > 0 && progress >= 0;
        Matcher eta = ETA.matcher(content);
        String etaText = "";
        int etaMinutes = -1;
        if (eta.find()) {
            try {
                etaMinutes = Integer.parseInt(eta.group(1));
                etaText = eta.group(1) + (eta.group(2) == null ? "" : "–" + eta.group(2)) + " min";
            } catch (NumberFormatException ignored) {
                etaMinutes = -1;
            }
        }

        boolean tracked = actualProgress || etaMinutes >= 0 || containsAny(normalized, TRACKING);
        if (!tracked) return null;

        int stage = containsAny(normalized, FINAL) ? 3
                : containsAny(normalized, EARLY) ? 1 : 2;
        float ratio = actualProgress ? clamp(progress / (float) max, 0f, 1f) : -1f;
        if (etaText.isEmpty()) {
            if (stage == 3) etaText = "Arriving";
            else if (actualProgress) {
                etaText = Math.round(ratio * 100f) + "%";
            } else {
                etaText = "Tracking";
            }
        }

        String statusTitle = title;
        if (statusTitle.isEmpty()) statusTitle = "Ride / order in progress";
        String secondary = body;
        if (secondary.isEmpty()) secondary = sub;
        Matcher distance = DISTANCE.matcher(content);
        if (distance.find()) {
            String value = distance.group(1) + " " + distance.group(2).toLowerCase(Locale.ROOT);
            if (!secondary.isEmpty() && !secondary.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) {
                secondary = value + " · " + secondary;
            } else if (secondary.isEmpty()) {
                secondary = value;
            }
        }
        return new Info(etaText, ratio, statusTitle, secondary, stage);
    }

    private static boolean containsAny(String text, String[] terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private static String first(Bundle extras, String... keys) {
        for (String key : keys) {
            CharSequence value = extras.getCharSequence(key);
            if (value != null && value.length() > 0) return value.toString();
        }
        return "";
    }

    private static String value(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
