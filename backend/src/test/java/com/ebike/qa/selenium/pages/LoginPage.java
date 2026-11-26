package com.ebike.qa.selenium.pages;

import com.ebike.qa.selenium.Ui;
import org.openqa.selenium.By;

public class LoginPage extends Page {
    public static final By IDENTITY = By.name("usernameOrEmail");
    public static final By PASSWORD = By.name("password");
    public static final By SUBMIT = By.cssSelector("#loginForm button.button");
    public static final By ERROR = By.id("error");
    public static final By SHOW_PW = By.id("showPw");
    public static final By NOTICE = By.cssSelector("p.notice");

    public LoginPage(Ui ui) { super(ui); }

    public LoginPage open() { ui.open("/login"); ui.visible(IDENTITY); return this; }

    public LoginPage fill(String identity, String password) {
        ui.type(IDENTITY, identity);
        ui.type(PASSWORD, password);
        return this;
    }

    public void submit() { ui.click(SUBMIT); }

    /** Signs in and waits until the page has left /login. */
    public void loginAs(String identity, String password) {
        open().fill(identity, password).submit();
        ui.until(d -> !d.getCurrentUrl().contains("/login"));
    }

    public String error() { ui.waitText(ERROR, " "); return ui.text(ERROR); }
}
