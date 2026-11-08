package dev.android.dynamicisland.island.source;

import android.app.Notification;
import android.os.Bundle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses turn-by-turn guidance from the existing System UI ongoing-activity notification.
 * This is a Java-only implementation for the Dynamic Island event model; it does not create
 * a listener, overlay, service or permission of its own.
 */
final class NavigationParser {
    enum Direction { LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, U_TURN, STRAIGHT, ROUNDABOUT, DESTINATION }

    static final class Info {
        final String distance;
        final String instruction;
        final String detail;
        final String eta;
        final Direction direction;

        Info(String distance, String instruction, String detail, String eta, Direction direction) {
            this.distance = distance;
            this.instruction = instruction;
            this.detail = detail;
            this.eta = eta;
            this.direction = direction;
        }
    }

    private static final Set<String> MAP_APPS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.maps", "com.google.android.apps.mapslite",
            "com.waze", "com.citymapper.app.release", "ru.yandex.yandexnavi",
            "ru.yandex.yandexmaps", "ru.dublgis.dgismobile", "com.here.app.maps",
            "com.sygic.aura", "com.sygic.truck", "com.tomtom.gplay.navapp",
            "com.tomtom.gplay.navapp.gofleet", "com.generalmagic.magicearth",
            "com.mapswithme.maps.pro", "app.organicmaps", "app.organicmaps.web",
            "app.comaps", "app.comaps.google", "app.comaps.fdroid", "app.comaps.huawei",
            "net.osmand", "net.osmand.plus", "net.osmand.dev", "net.osmand.huawei",
            "cz.seznam.mapy", "com.alk.copilot.mapviewer", "nl.flitsmeister",
            "nl.flitsmeister.flux", "com.huawei.maps.app", "com.autonavi.minimap",
            "com.baidu.BaiduMap", "com.tencent.map", "com.locnall.KimGiSa",
            "com.nhn.android.nmap", "net.daum.android.map", "com.skt.tmap.ku",
            "com.mmi.maps", "com.mapmyindia.maps"
    ));

    /** Never reinterpret these live order / ride notifications as turn-by-turn navigation. */
    private static final Set<String> LIVE_ACTIVITY_APPS = new HashSet<>(Arrays.asList(
            "com.ubercab", "me.lyft.android", "com.grabtaxi.passenger", "com.gojek.app",
            "xyz.be.customer", "com.taxsee.taxsee", "com.olacabs.customer",
            "com.rapido.passenger", "ee.mtakso.client", "ru.yandex.taxi",
            "com.careem.acma", "sinet.startup.inDriver", "in.juspay.nammayatri",
            "com.application.zomato", "in.swiggy.android", "com.grofers.customerapp",
            "com.zepto.consumer", "com.dunzo.user", "com.Dominos", "com.ubercab.eats",
            "com.dd.doordash", "com.postmates.android", "com.deliveroo.orderapp",
            "com.glovo", "com.wolt.android", "com.grubhub.android",
            "com.global.foodpanda.android", "com.talabat", "ru.yandex.eda",
            "com.grability.rappi", "br.com.brainweb.ifood", "com.bigbasket.mobileapp"
    ));

    private static final Pattern LEADING_DISTANCE = Pattern.compile(
            "^\\s*(?:(?:in|after|within|через|dans|em|dentro de|en|tra|za)\\s+)?"
                    + "([\\p{Nd}]+(?:[.,][\\p{Nd}]+)?)\\s*"
                    + "(km|kilometers?|kilometres?|mi|miles?|m|meters?|metres?|ft|feet|yd|yards?|"
                    + "км|м|метр(?:а|ов)?|公里|千米|米|キロメートル|メートル|킬로미터|미터|"
                    + "كم|كيلومتر|كيلومترات|متر|किमी|किलोमीटर|मीटर)"
                    + "(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ANY_DISTANCE = Pattern.compile(
            "([\\p{Nd}]+(?:[.,][\\p{Nd}]+)?)\\s*"
                    + "(km|kilometers?|kilometres?|mi|miles?|m|meters?|metres?|ft|feet|yd|yards?|"
                    + "км|м|метр(?:а|ов)?|公里|千米|米|キロメートル|メートル|킬로미터|미터|"
                    + "كم|كيلومتر|كيلومترات|متر|किमी|किलोमीटर|मीटर)"
                    + "(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRIP_STAT = Pattern.compile(
            "(?i)(?:\\beta\\b|\\barriv(?:e|al|ed)\\b|\\bremaining\\b|\\b"
                    + "[\\p{Nd}]+(?:[.,][\\p{Nd}]+)?\\s*(?:min|mins|minutes?|h|hr|hrs|hours?|"
                    + "km|mi|miles?|км|分钟|分鐘|分|분|دقيقة|دقائق|मिनट|小时|時間|시간)\\b"
                    + "|\\b\\d{1,2}:\\d{2}\\b|到达|到達|到着|도착|الوصول|पहुंच)"
    );
    private static final Pattern SEGMENT_SPLIT = Pattern.compile("\\s*[\\u2022\\u00B7|]\\s*");

    private static final String[] LEFT = {
            "turn left", "go left", "head left", "keep left", "bear left", "left onto",
            "left on", "sharp left", "поверните налево", "налево", "влево", "ліворуч",
            "tournez à gauche", "à gauche", "links abbiegen", "nach links",
            "gire a la izquierda", "a la izquierda", "vire à esquerda", "à esquerda",
            "svolta a sinistra", "linksaf", "sola dön", "skręć w lewo", "belok kiri",
            "rẽ trái", "sang trái", "左转", "左轉", "向左折", "左折", "좌회전", "왼쪽으로",
            "انعطف يسارا", "انعطف يسارًا", "बाएं मुड़ें"
    };
    private static final String[] RIGHT = {
            "turn right", "go right", "head right", "keep right", "bear right", "right onto",
            "right on", "sharp right", "поверните направо", "направо", "вправо", "праворуч",
            "tournez à droite", "à droite", "rechts abbiegen", "nach rechts",
            "gire a la derecha", "a la derecha", "vire à direita", "à direita",
            "svolta a destra", "rechtsaf", "sağa dön", "skręć w prawo", "belok kanan",
            "rẽ phải", "sang phải", "右转", "右轉", "向右折", "右折", "우회전", "오른쪽으로",
            "انعطف يمينا", "انعطف يمينًا", "दाएं मुड़ें"
    };
    private static final String[] SLIGHT_LEFT = {
            "slight left", "bear left", "keep left", "veer left", "держитесь левее",
            "légèrement à gauche", "leicht links", "ligeramente a la izquierda",
            "levemente à esquerda", "leggermente a sinistra", "hafif sola", "łagodnie w lewo",
            "sedikit ke kiri", "đi chếch sang trái", "稍向左", "斜め左", "좌측 방향", "قليلا لليسار"
    };
    private static final String[] SLIGHT_RIGHT = {
            "slight right", "bear right", "keep right", "veer right", "держитесь правее",
            "légèrement à droite", "leicht rechts", "ligeramente a la derecha",
            "levemente à direita", "leggermente a destra", "hafif sağa", "łagodnie w prawo",
            "sedikit ke kanan", "đi chếch sang phải", "稍向右", "斜め右", "우측 방향", "قليلا لليمين"
    };
    private static final String[] U_TURN = {
            "u-turn", "u turn", "turn around", "make a u turn", "разворот", "розворот",
            "demi-tour", "wenden", "media vuelta", "retorno", "faça o retorno", "inversione a u",
            "u dönüşü", "zawróć", "putar balik", "quay đầu", "掉头", "調頭", "uターン", "유턴",
            "انعطاف للخلف", "यू-टर्न"
    };
    private static final String[] ROUNDABOUT = {
            "roundabout", "rotary", "traffic circle", "кольцо", "круговое движение",
            "rond-point", "kreisverkehr", "rotonda", "glorieta", "rotatória",
            "döner kavşak", "rondo", "bundaran", "vòng xuyến", "环岛", "環島",
            "ロータリー", "회전교차로", "دوار", "गोल चक्कर"
    };
    private static final String[] DESTINATION = {
            "you have arrived", "arrived at", "destination reached", "arrived", "reached destination",
            "вы прибыли", "прибыли", "vous êtes arrivé", "ziel erreicht", "ha llegado",
            "chegou ao destino", "sei arrivato", "bestemming bereikt", "hedefe ulaştınız",
            "dotarłeś do celu", "anda telah tiba", "bạn đã đến nơi", "已到达", "到達目的地",
            "目的地に到着", "목적지에 도착", "لقد وصلت", "आप पहुंच गए"
    };
    private static final String[] DELIVERY_WORDS = {
            "your order", "order is", "delivery", "courier", "rider", "driver arriving",
            "restaurant", "food order", "pickup", "pick-up", "taxi", "ride is", "fare",
            "заказ", "курьер", "доставка", "водитель", "pedido", "repartidor", "entrega",
            "commande", "livreur", "bestellung", "lieferung", "sipariş", "kurye",
            "订单", "訂單", "外卖", "骑手", "配送", "司机", "배달", "주문", "توصيل", "ऑर्डर"
    };
    private static final String[] MANEUVER_WORDS = {
            "turn", "continue", "head", "exit", "merge", "ramp", "fork", "roundabout",
            "destination", "straight", "onto", "take the", "go left", "go right",
            "прямо", "съезд", "выезд", "двигайтесь", "поверните", "продовжуйте",
            "continuez", "sortie", "biegen sie", "ausfahrt", "gira a", "siga em frente",
            "svolta", "afslag", "çıkış", "skręć", "zjazd", "đi thẳng", "直行", "出口",
            "直進", "진입", "직진", "انعطف", "استمر", "सीधे", "निकास"
    };

    private NavigationParser() { }

    static boolean isNavigation(Notification notification, String packageName) {
        return parse(notification, packageName) != null;
    }

    static Info parse(Notification notification, String packageName) {
        if (notification == null || packageName == null || LIVE_ACTIVITY_APPS.contains(packageName)) return null;
        if (packageName.contains("screenrecorder") || packageName.contains("voicenote")
                || packageName.contains("soundrecorder") || packageName.contains("hotspot")) return null;

        String title = text(notification.extras, Notification.EXTRA_TITLE);
        String body = firstText(notification.extras, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT);
        String sub = text(notification.extras, Notification.EXTRA_SUB_TEXT);
        String combined = (title + " " + body + " " + sub).toLowerCase(Locale.ROOT).trim();
        if (combined.isEmpty()) return null;

        boolean category = "navigation".equals(notification.category);
        boolean knownApp = MAP_APPS.contains(packageName);
        boolean ongoing = (notification.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0;
        if (!category && (!knownApp || !ongoing)) return null;

        // A category flag is not sufficient on its own: some delivery and ride notifications
        // use it too. For unknown packages require an explicit guidance instruction.
        if (!knownApp && (!category || containsAny(combined, DELIVERY_WORDS))) return null;
        if (combined.contains("screen recording") || combined.contains("recording audio")
                || combined.contains("hotspot is on") || combined.contains("call in progress")) return null;

        Direction direction = direction(combined);
        boolean hasManeuver = direction != Direction.STRAIGHT || containsAny(combined, MANEUVER_WORDS);
        Matcher leading = LEADING_DISTANCE.matcher(title.trim());
        Matcher any = ANY_DISTANCE.matcher(title + " " + body);
        String distanceMatch = leading.find() ? leading.group() : any.find() ? any.group() : "";
        boolean hasTripStats = TRIP_STAT.matcher(combined).find();
        if (knownApp) {
            if (!category && !hasManeuver && distanceMatch.isEmpty() && !hasTripStats) return null;
        } else if (!hasManeuver) {
            return null;
        }

        String instruction = stripLeadingDistance(title);
        if (instruction.isEmpty()) instruction = body.trim();
        if (instruction.isEmpty()) instruction = "Navigation";

        String detail = "";
        if (!sub.trim().isEmpty()) detail = sub.trim();
        else if (!body.trim().isEmpty() && !body.trim().equals(instruction)) detail = body.trim();

        String eta = tripStats(body);
        if (eta.isEmpty() && hasTripStats && body.contains("•")) eta = body.trim();

        String distance = "";
        if (!distanceMatch.isEmpty()) {
            boolean hasPrefix = Character.isLetter(distanceMatch.charAt(0))
                    || distanceMatch.charAt(0) > 127;
            distance = hasPrefix || distanceMatch.charAt(0) > 127 ? distanceMatch.trim() : "In " + distanceMatch.trim();
        }
        return new Info(distance, instruction, detail, eta, direction);
    }

    private static Direction direction(String content) {
        if (containsAny(content, U_TURN)) return Direction.U_TURN;
        if (containsAny(content, SLIGHT_LEFT)) return Direction.SLIGHT_LEFT;
        if (containsAny(content, SLIGHT_RIGHT)) return Direction.SLIGHT_RIGHT;
        if (containsAny(content, LEFT)) return Direction.LEFT;
        if (containsAny(content, RIGHT)) return Direction.RIGHT;
        if (containsAny(content, ROUNDABOUT)) return Direction.ROUNDABOUT;
        if (containsAny(content, DESTINATION)) return Direction.DESTINATION;
        return Direction.STRAIGHT;
    }

    private static String stripLeadingDistance(String title) {
        Matcher matcher = LEADING_DISTANCE.matcher(title == null ? "" : title.trim());
        if (!matcher.find()) return title == null ? "" : title.trim();
        return title.trim().substring(matcher.end()).replaceFirst("^[\\s:•·|—-]+", "").trim();
    }

    private static String tripStats(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] parts = SEGMENT_SPLIT.split(text);
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String candidate = part.trim();
            if (candidate.isEmpty()) continue;
            if (TRIP_STAT.matcher(candidate).find() || candidate.matches("(?i)\\d+(?:[.,]\\d+)?\\s*(?:km|mi|miles?)")) {
                if (out.length() > 0) out.append(" • ");
                out.append(candidate);
            }
        }
        return out.toString();
    }

    private static String firstText(Bundle extras, String first, String second) {
        String a = text(extras, first);
        return a.isEmpty() ? text(extras, second) : a;
    }

    private static String text(Bundle extras, String key) {
        if (extras == null) return "";
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }

    private static boolean containsAny(String text, String[] values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
