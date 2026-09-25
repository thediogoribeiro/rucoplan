package pt.rucodel.productionplanning.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    @GetMapping("/admin/system-diagnostics/realtime")
    public String realtimeDiagnosticsPage() {
        return "redirect:/admin.html#diagnostics";
    }
}
