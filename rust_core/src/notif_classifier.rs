//! Engine 2: Notification Priority Classifier & Global Focus Rules Engine
//!
//! Categorizes incoming notifications into 6 semantic buckets, resolves their priority
//! level (`P1 Critical` through `P4 Low`), and enforces global Focus Rules (`AllowAll`,
//! `MuteSilent`, `HideCompletely`).

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum NotificationCategory {
    SecurityAndUrgent = 0,
    DirectMessages = 1,
    WorkAndCalendar = 2,
    SocialAndCommunity = 3,
    NewsAndMedia = 4,
    PromotionsAndDeals = 5,
}

impl NotificationCategory {
    pub fn default_priority(self) -> PriorityLevel {
        match self {
            NotificationCategory::SecurityAndUrgent => PriorityLevel::Critical,
            NotificationCategory::DirectMessages => PriorityLevel::High,
            NotificationCategory::WorkAndCalendar => PriorityLevel::High,
            NotificationCategory::SocialAndCommunity => PriorityLevel::Medium,
            NotificationCategory::NewsAndMedia => PriorityLevel::Low,
            NotificationCategory::PromotionsAndDeals => PriorityLevel::Low,
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            NotificationCategory::SecurityAndUrgent => "Security, OTP & Urgent",
            NotificationCategory::DirectMessages => "Direct Messages & Chats",
            NotificationCategory::WorkAndCalendar => "Work, Tasks & Calendar",
            NotificationCategory::SocialAndCommunity => "Social & Community",
            NotificationCategory::NewsAndMedia => "News, Media & Digests",
            NotificationCategory::PromotionsAndDeals => "Promotions & Marketing",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
#[repr(u8)]
pub enum PriorityLevel {
    Low = 1,
    Medium = 2,
    High = 3,
    Critical = 4,
}

impl PriorityLevel {
    pub fn badge_label(self) -> &'static str {
        match self {
            PriorityLevel::Critical => "P1 • CRITICAL",
            PriorityLevel::High => "P2 • HIGH",
            PriorityLevel::Medium => "P3 • MEDIUM",
            PriorityLevel::Low => "P4 • LOW",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum FocusRuleAction {
    AllowAll = 0,
    MuteSilent = 1,
    HideCompletely = 2,
}

impl FocusRuleAction {
    pub fn badge_text(self) -> &'static str {
        match self {
            FocusRuleAction::AllowAll => "ALLOWED",
            FocusRuleAction::MuteSilent => "MUTED",
            FocusRuleAction::HideCompletely => "HIDDEN",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PriorityRuleTable {
    pub critical_action: FocusRuleAction,
    pub high_action: FocusRuleAction,
    pub medium_action: FocusRuleAction,
    pub low_action: FocusRuleAction,
    pub smart_urgent_breakthrough_enabled: bool,
}

impl Default for PriorityRuleTable {
    fn default() -> Self {
        Self {
            critical_action: FocusRuleAction::AllowAll,
            high_action: FocusRuleAction::MuteSilent,
            medium_action: FocusRuleAction::HideCompletely,
            low_action: FocusRuleAction::HideCompletely,
            smart_urgent_breakthrough_enabled: true,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ClassificationOutcome {
    pub category: NotificationCategory,
    pub priority: PriorityLevel,
    pub action: FocusRuleAction,
    pub matched_urgent_breakthrough: bool,
    pub reason: &'static str,
}

pub fn classify_and_evaluate_notification(
    package_name: &str,
    title: &str,
    body: &str,
    rules: &PriorityRuleTable,
    category_priority_override: Option<PriorityLevel>,
) -> ClassificationOutcome {
    let pkg_lower = package_name.to_ascii_lowercase();
    let combined_lower = format!("{} {}", title, body).to_ascii_lowercase();

    // 1. Check critical OTP / 2FA / Emergency / Ride-sharing signals first
    let has_urgent_keyword = combined_lower.contains("urgent")
        || combined_lower.contains("emergency")
        || combined_lower.contains("verification code")
        || combined_lower.contains("security code")
        || combined_lower.contains("one-time")
        || combined_lower.contains("otp")
        || combined_lower.contains("2fa")
        || combined_lower.contains("your driver")
        || combined_lower.contains("arriving now");

    let is_authenticator_pkg = pkg_lower.contains("authenticator")
        || pkg_lower.contains("authy")
        || pkg_lower.contains("bank")
        || pkg_lower.contains("uber")
        || pkg_lower.contains("lyft");

    if has_urgent_keyword || is_authenticator_pkg {
        let priority = category_priority_override.unwrap_or(PriorityLevel::Critical);
        let action = if rules.smart_urgent_breakthrough_enabled {
            FocusRuleAction::AllowAll
        } else {
            resolve_action_for_priority(priority, rules)
        };
        return ClassificationOutcome {
            category: NotificationCategory::SecurityAndUrgent,
            priority,
            action,
            matched_urgent_breakthrough: rules.smart_urgent_breakthrough_enabled,
            reason: "Matched OTP/2FA/Urgent breakthrough signature",
        };
    }

    // 2. Promotions & Deals detection
    let is_promo = combined_lower.contains("% off")
        || combined_lower.contains("flash sale")
        || combined_lower.contains("coupon")
        || combined_lower.contains("promo code")
        || combined_lower.contains("limited-time deal")
        || pkg_lower.contains("shopping")
        || pkg_lower.contains("amazon");

    if is_promo {
        let priority = category_priority_override.unwrap_or(PriorityLevel::Low);
        return ClassificationOutcome {
            category: NotificationCategory::PromotionsAndDeals,
            priority,
            action: resolve_action_for_priority(priority, rules),
            matched_urgent_breakthrough: false,
            reason: "Matched promotional/shopping keyword or package",
        };
    }

    // 3. Work & Calendar detection
    let is_work = pkg_lower.contains("slack")
        || pkg_lower.contains("teams")
        || pkg_lower.contains("calendar")
        || pkg_lower.contains("outlook")
        || pkg_lower.contains("gm")
        || pkg_lower.contains("jira")
        || combined_lower.contains("meeting")
        || combined_lower.contains("deployment");

    if is_work {
        let priority = category_priority_override.unwrap_or(PriorityLevel::High);
        return ClassificationOutcome {
            category: NotificationCategory::WorkAndCalendar,
            priority,
            action: resolve_action_for_priority(priority, rules),
            matched_urgent_breakthrough: false,
            reason: "Matched enterprise workspace or calendar package",
        };
    }

    // 4. Direct Messages & Chats detection
    let is_dm = pkg_lower.contains("whatsapp")
        || pkg_lower.contains("telegram")
        || pkg_lower.contains("signal")
        || pkg_lower.contains("mms")
        || pkg_lower.contains("messaging")
        || pkg_lower.contains("discord");

    if is_dm {
        let priority = category_priority_override.unwrap_or(PriorityLevel::High);
        return ClassificationOutcome {
            category: NotificationCategory::DirectMessages,
            priority,
            action: resolve_action_for_priority(priority, rules),
            matched_urgent_breakthrough: false,
            reason: "Matched direct messaging or chat package",
        };
    }

    // 5. News & Media detection
    let is_news = pkg_lower.contains("news")
        || pkg_lower.contains("nytimes")
        || pkg_lower.contains("bbc")
        || pkg_lower.contains("podcast")
        || combined_lower.contains("breaking news")
        || combined_lower.contains("daily digest");

    if is_news {
        let priority = category_priority_override.unwrap_or(PriorityLevel::Low);
        return ClassificationOutcome {
            category: NotificationCategory::NewsAndMedia,
            priority,
            action: resolve_action_for_priority(priority, rules),
            matched_urgent_breakthrough: false,
            reason: "Matched news or digest signature",
        };
    }

    // 6. Default to Social & Community
    let priority = category_priority_override.unwrap_or(PriorityLevel::Medium);
    ClassificationOutcome {
        category: NotificationCategory::SocialAndCommunity,
        priority,
        action: resolve_action_for_priority(priority, rules),
        matched_urgent_breakthrough: false,
        reason: "Classified under Social & Community notifications",
    }
}

pub fn resolve_action_for_priority(
    priority: PriorityLevel,
    rules: &PriorityRuleTable,
) -> FocusRuleAction {
    match priority {
        PriorityLevel::Critical => rules.critical_action,
        PriorityLevel::High => rules.high_action,
        PriorityLevel::Medium => rules.medium_action,
        PriorityLevel::Low => rules.low_action,
    }
}
