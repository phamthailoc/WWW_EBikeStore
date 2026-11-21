package com.ebike.service.warranty;

import java.time.Period;
import java.time.format.DateTimeParseException;

/** Turns a stored milestone such as P6M into words for a notification or an email. */
public final class MaintenanceMilestones {

    private MaintenanceMilestones() {
    }

    public static String describe(String milestone) {
        return describe(milestone, "vi");
    }

    public static String describe(String milestone, String language) {
        boolean english = "en".equals(WarrantyMessages.normalize(language));
        try {
            Period period = Period.parse(milestone).normalized();
            long months = period.toTotalMonths();
            if (months > 0 && period.getDays() == 0) {
                return english ? months + (months == 1 ? " month" : " months") : months + " tháng";
            }
            if (months == 0 && period.getDays() > 0) {
                return english ? period.getDays() + (period.getDays() == 1 ? " day" : " days") : period.getDays() + " ngày";
            }
            return english ? months + " months " + period.getDays() + " days" : months + " tháng " + period.getDays() + " ngày";
        } catch (DateTimeParseException exception) {
            return milestone;
        }
    }
}
