package com.brixo.slidehub.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocsController {

    @GetMapping("/ai-guide")
    public String aiGuide() {
        return "ai-guide";
    }
}
