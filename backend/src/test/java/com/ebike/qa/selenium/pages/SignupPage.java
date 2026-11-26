package com.ebike.qa.selenium.pages;

import com.ebike.qa.selenium.Ui;
import org.openqa.selenium.By;

public class SignupPage extends Page {
    public static final By FIRST = By.name("firstName");
    public static final By LAST = By.name("lastName");
    public static final By USER = By.name("username");
    public static final By EMAIL = By.name("email");
    public static final By PASSWORD = By.name("password");
    public static final By SUBMIT = By.cssSelector("#signupForm button.button");
    public static final By ERROR = By.id("error");

    public SignupPage(Ui ui) { super(ui); }

    public SignupPage open() { ui.open("/signup"); ui.visible(USER); return this; }

    public SignupPage fill(String first, String last, String user, String email, String password) {
        ui.type(FIRST, first);
        ui.type(LAST, last);
        ui.type(USER, user);
        ui.type(EMAIL, email);
        ui.type(PASSWORD, password);
        return this;
    }

    public void submit() { ui.click(SUBMIT); }

    /** True when the browser's own validation blocks the field. */
    public boolean invalid(By field) { return Boolean.FALSE.equals(ui.js("return arguments[0].checkValidity();", ui.present(field))); }

    public String error() { ui.waitText(ERROR, " "); return ui.text(ERROR); }
}
