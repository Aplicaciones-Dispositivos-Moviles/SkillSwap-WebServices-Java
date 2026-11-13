# Going from simulated billing to real RevenueCat

Today the backend runs with `REVENUECAT_API_KEY` empty: `SimulatedPaymentGatewayAdapter` approves a purchase and
nothing is charged (state in memory, lost on restart). Everything below is only needed to charge real money.
The code needs no change: setting the variables switches `SubscriptionBillingConfig` to `RevenueCatGatewayAdapter`.

## 1. Google Play Console
- [ ] Developer account (one-time fee of about US$25).
- [ ] The app created and an internal-testing track with at least one build uploaded.
- [ ] Subscription product `premium_monthly` (same id as `SubscriptionPlan.DEFAULT_MONTHLY_PRODUCT_ID`) with a base plan.
- [ ] License testers added (their Google accounts), so purchases in testing are free.

## 2. RevenueCat
- [ ] Project created and the Google Play app connected (service-account credentials from Play Console).
- [ ] Product imported, entitlement `premium` created and the product attached to it.
- [ ] The app's `appUserID` set to the SkillSwap student id (the backend reads it as the student id).
- [ ] Secret API key (v1/v2 read) copied.

## 3. Render environment variables
| Variable | Value |
|---|---|
| `REVENUECAT_API_KEY` | secret API key |
| `REVENUECAT_ENTITLEMENT_ID` | `premium` (default) |
| `REVENUECAT_WEBHOOK_AUTH` | a long random secret you invent |
| `REVENUECAT_ACCEPT_SANDBOX` | `true` while testing, `false` in production |

## 4. Webhook
- [ ] RevenueCat > Integrations > Webhooks: URL `https://<backend>/api/v1/subscriptions/webhooks/revenuecat`.
- [ ] Authorization header value = exactly `REVENUECAT_WEBHOOK_AUTH`.
- [ ] "Send test event" must answer 200 (a wrong secret is rejected).

## 5. Check
- [ ] Buy in the app with a license tester, then `POST /api/v1/subscriptions` answers ACTIVE.
- [ ] Cancel in Play: the webhook moves it to CANCELLED, premium stays until the period end.
- [ ] The Render log no longer shows "purchases are SIMULATED".
