package org.acme.views;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

public class LogonResource {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance logon(String mensagem);
    }
}