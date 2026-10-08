package com.post.components.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ComponentsController {

    @GetMapping("/sidebar")
    public String sidebar(){
        return "components/sidebar";
    }
}
