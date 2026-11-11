//! Sentinal Rust Core Library (`sentinal_rust_core`)
//!
//! Exposes the 3 core deterministic engines:
//! 1. `focus_fsm`: Focus State Machine & Schedule Evaluation Engine
//! 2. `notif_classifier`: Notification Priority Classifier & Focus Rules Engine
//! 3. `call_bouncer`: Call Bouncer Cooldown & Repeat-Caller Matcher

pub mod call_bouncer;
pub mod focus_fsm;
pub mod jni_bridge;
pub mod notif_classifier;

#[cfg(test)]
mod tests {
    use super::call_bouncer::*;
    use super::focus_fsm::*;
    use super::notif_classifier::*;

    #[test]
    fn test_manual_turn_off_suppresses_game_until_exit() {
        let initial = FocusEngineState {
            active_mode: FocusMode::Gaming,
            active_game_package: Some("com.supercell.clashofclans".to_string()),
            session_start_ms: Some(1_000_000),
            ..Default::default()
        };

        // User turns off Gaming Mode from notification bar while game is running
        let outcome = step_focus_state_machine(
            &initial,
            FocusEvent::ManualSetMode {
                target_mode: FocusMode::Off,
                duration_minutes: None,
                now_ms: 2_500_000,
            },
        );
        assert_eq!(outcome.state.active_mode, FocusMode::Off);
        assert!(outcome.state.manually_dismissed_gaming_whilst_in_game);
        assert_eq!(
            outcome.state.declined_game_package_for_session.as_deref(),
            Some("com.supercell.clashofclans")
        );

        // Game remains in foreground -> must NOT re-prompt or auto-activate
        let poll_same_game = step_focus_state_machine(
            &outcome.state,
            FocusEvent::ForegroundAppObserved {
                package_name: "com.supercell.clashofclans".to_string(),
                is_game: true,
                now_ms: 2_505_000,
            },
        );
        assert_eq!(poll_same_game.state.active_mode, FocusMode::Off);
        assert!(poll_same_game.commands.is_empty());

        // User exits game to launcher -> resets suppression
        let exited = step_focus_state_machine(
            &poll_same_game.state,
            FocusEvent::ForegroundAppObserved {
                package_name: "com.google.android.apps.nexuslauncher".to_string(),
                is_game: false,
                now_ms: 2_510_000,
            },
        );
        assert!(!exited.state.manually_dismissed_gaming_whilst_in_game);
        assert!(exited.state.declined_game_package_for_session.is_none());
    }

    #[test]
    fn test_notification_classifier_otp_vs_promo() {
        let rules = PriorityRuleTable::default();
        let otp = classify_and_evaluate_notification(
            "com.google.android.apps.authenticator2",
            "Security Verification Code",
            "Your one-time OTP code is 491-820",
            &rules,
            None,
        );
        assert_eq!(otp.category, NotificationCategory::SecurityAndUrgent);
        assert_eq!(otp.priority, PriorityLevel::Critical);
        assert_eq!(otp.action, FocusRuleAction::AllowAll);

        let promo = classify_and_evaluate_notification(
            "com.amazon.mShop.android.shopping",
            "50% OFF Flash Sale",
            "Use coupon code SAVE50 tonight",
            &rules,
            None,
        );
        assert_eq!(promo.category, NotificationCategory::PromotionsAndDeals);
        assert_eq!(promo.priority, PriorityLevel::Low);
        assert_eq!(promo.action, FocusRuleAction::HideCompletely);
    }

    #[test]
    fn test_call_bouncer_repeat_breakthrough_and_outgoing_guard() {
        let policy = CallPolicyConfig {
            decline_all_calls_regardless: false,
            strict_gaming_lockdown: false,
            is_gaming_mode_active: false,
            allow_starred_contacts: true,
            allow_repeat_callers_within_3m: true,
            auto_reply_enabled: true,
        };

        // 1st call from +1 (415) 555-0199 -> bounced + auto-reply sent
        let first = evaluate_incoming_call(
            "+1 (415) 555-0199",
            false,
            None,
            None,
            None,
            1_000_000,
            &policy,
        );
        assert_eq!(first.normalized_caller_key, "155550199");
        assert_eq!(first.filter_action, CallFilterAction::BounceAndDeclineCall);
        assert!(first.dispatch_auto_reply);

        // 2nd call 90s later from 415-555-0199 -> breaks through!
        let second = evaluate_incoming_call(
            "415-555-0199",
            false,
            first.updated_last_call_timestamp_ms,
            None,
            first.updated_last_auto_reply_timestamp_ms,
            1_090_000,
            &policy,
        );
        assert_eq!(second.filter_action, CallFilterAction::AllowRingBreakthrough);
        assert!(!second.dispatch_auto_reply);
    }
}
