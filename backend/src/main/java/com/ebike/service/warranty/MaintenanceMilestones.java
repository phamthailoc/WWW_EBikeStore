package com.ebike.service.warranty;

import java.time.Period;
import java.time.format.DateTimeParseException;

/** Turns a stored milestone such as P6M into words for a notification or an email. */
public final class MaintenanceMilestones {

    private MaintenanceMilestones() {
    }

    public static String describe(String milestone) {
        try {
            Period period = Period.parse(milestone).normalized();
            long months = period.toTotalMonths();
            if (months > 0 && period.getDays() == 0) {
                return months + " tháng";
            }
            if (months == 0 && period.getDays() > 0) {
                return period.getDays() + " ngày";
            }
            return months + " tháng " + period.getDays() + " ngày";
        } catch (DateTimeParseException exception) {
            return milestone;
        }
    }
}
