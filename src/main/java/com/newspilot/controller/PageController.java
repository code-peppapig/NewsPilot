package com.newspilot.controller;

import com.newspilot.entity.AppUser;
import com.newspilot.entity.Briefing;
import com.newspilot.entity.Subscription;
import com.newspilot.repository.AppUserRepository;
import com.newspilot.repository.BriefingRepository;
import com.newspilot.repository.SubscriptionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 页面控制器：处理用户身份填写、订阅偏好设置、历史简报查看。
 */
@Controller
public class PageController {

    private final AppUserRepository appUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BriefingRepository briefingRepository;

    public PageController(AppUserRepository appUserRepository,
                          SubscriptionRepository subscriptionRepository,
                          BriefingRepository briefingRepository) {
        this.appUserRepository = appUserRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.briefingRepository = briefingRepository;
    }

    // ==================== 首页：填写身份信息 ====================

    @GetMapping("/")
    public String index(Model model) {
        List<AppUser> users = appUserRepository.findAll();
        model.addAttribute("users", users);
        return "index";
    }

    @PostMapping("/user")
    public String saveUser(@RequestParam String name,
                           @RequestParam String identity,
                           @RequestParam String email) {
        // 如果邮箱已存在则更新，否则新建
        Optional<AppUser> existing = appUserRepository.findByEmail(email);
        AppUser user;
        if (existing.isPresent()) {
            user = existing.get();
            user.setName(name);
            user.setIdentity(identity);
        } else {
            user = new AppUser();
            user.setName(name);
            user.setIdentity(identity);
            user.setEmail(email);
        }
        appUserRepository.save(user);
        return "redirect:/preferences?userId=" + user.getId();
    }

    // ==================== 偏好设置 ====================

    @GetMapping("/preferences")
    public String preferences(@RequestParam(required = false) Long userId, Model model) {
        List<AppUser> users = appUserRepository.findAll();
        model.addAttribute("users", users);

        if (userId != null) {
            Optional<Subscription> sub = subscriptionRepository.findByUserId(userId);
            sub.ifPresent(s -> model.addAttribute("subscription", s));
            model.addAttribute("selectedUserId", userId);
        }
        return "preferences";
    }

    @PostMapping("/preferences")
    public String savePreferences(@RequestParam Long userId,
                                   @RequestParam String topics,
                                   @RequestParam String keywords) {
        Optional<Subscription> existing = subscriptionRepository.findByUserId(userId);
        Subscription sub;
        if (existing.isPresent()) {
            sub = existing.get();
            sub.setTopics(topics);
            sub.setKeywords(keywords);
        } else {
            sub = new Subscription();
            sub.setUserId(userId);
            sub.setTopics(topics);
            sub.setKeywords(keywords);
        }
        subscriptionRepository.save(sub);
        return "redirect:/briefings";
    }

    // ==================== 历史简报 ====================

    @GetMapping("/briefings")
    public String briefings(@RequestParam(required = false) Long userId, Model model) {
        List<AppUser> users = appUserRepository.findAll();
        model.addAttribute("users", users);

        if (userId != null) {
            List<Briefing> briefings = briefingRepository.findByUserIdOrderByBriefingDateDesc(userId);
            model.addAttribute("briefings", briefings);
            model.addAttribute("selectedUserId", userId);
        } else {
            // 展示所有简报
            List<Briefing> briefings = briefingRepository.findAll();
            briefings.sort((a, b) -> b.getBriefingDate().compareTo(a.getBriefingDate()));
            model.addAttribute("briefings", briefings);
        }
        return "briefings";
    }

    // ==================== 简报详情 ====================

    @GetMapping("/briefing/{id}")
    public String briefingDetail(@PathVariable Long id, Model model) {
        Optional<Briefing> briefing = briefingRepository.findById(id);
        if (briefing.isEmpty()) {
            return "redirect:/briefings";
        }
        model.addAttribute("briefing", briefing.get());
        return "briefing-detail";
    }
}
