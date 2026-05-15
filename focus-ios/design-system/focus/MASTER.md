# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** Focus  
**Generated:** 2026-03-31  
**Category:** Live Streaming Privacy Protection App  
**Platform:** iOS / SwiftUI

---

## Product Definition

- **Primary user:** Live streamers
- **Core promise:** Let streamers keep broadcasting without worrying about bystanders' portrait rights.
- **Public promise:** Let citizens move through real-world spaces without anxiety about being shown on a live broadcast.
- **Primary privacy mode:** Avatar replacement
- **Optional privacy modes:** Blur, Off
- **Primary emotion:** Safe control in real time
- **Secondary emotion:** Professional creator tool, not a toy

## Design Intent

This product should feel like a **trustworthy creator safety tool**.
Not corporate surveillance software, not playful social media, and not a flashy gaming overlay.

### Visual Direction

- **Base style:** Swiss Modernism 2.0
- **Trust layer:** Trust & Authority
- **Conversion / reassurance layer:** Social Proof-Focused
- **UI personality:** Clean, calm, rational, creator-friendly, privacy-first

### Brand Tone

- **Keywords:** trustworthy, protective, live, clear, modern, ethical, professional
- **Avoid tone:** flashy, childish, chaotic, aggressive, creepy, punitive

---

## Global Rules

### Color Palette

Source direction synthesized from UI/UX Pro Max trust/security recommendations.

| Role | Hex | Token | Usage |
|------|-----|-------|-------|
| Primary | `#0369A1` | `--color-primary` | Navigation accents, key highlights, trusted system color |
| Secondary | `#0EA5E9` | `--color-secondary` | Selected states, chips, supportive emphasis |
| CTA / Protected | `#22C55E` | `--color-cta` | "Ready", "Protected", primary action, safe-confirm states |
| Background | `#F0F9FF` | `--color-background` | App background, light panels |
| Text | `#0C4A6E` | `--color-text` | Main text, headings, labels |
| Surface | `#FFFFFF` | `--color-surface` | Cards, sheets, floating panels |
| Muted Line | `#D7EAF5` | `--color-border` | Dividers, soft borders |
| Warning | `#F59E0B` | `--color-warning` | Attention state, not for primary branding |
| Danger | `#DC2626` | `--color-danger` | Stop / destructive only |
| Disabled | `#94A3B8` | `--color-disabled` | Off state, inactive controls |

**Color Notes:** Security blue + protected green. The system should feel safe and credible first, then responsive.

### SwiftUI Mapping

```swift
enum FocusTheme {
    static let primary = Color(hex: 0x0369A1)
    static let secondary = Color(hex: 0x0EA5E9)
    static let cta = Color(hex: 0x22C55E)
    static let background = Color(hex: 0xF0F9FF)
    static let text = Color(hex: 0x0C4A6E)
    static let surface = Color.white
    static let border = Color(hex: 0xD7EAF5)
    static let disabled = Color(hex: 0x94A3B8)
}
```

### Typography

Source direction synthesized from UI/UX Pro Max `Corporate Trust`.

- **Heading Font:** Lexend
- **Body Font:** Source Sans 3
- **Korean Fallback:** Noto Sans KR or system sans
- **Mood:** trustworthy, accessible, readable, professional, clean
- **Usage:** Rounded confidence for headings, calm legibility for body copy

### Type Scale

| Token | Size | Weight | Usage |
|------|------|--------|-------|
| `display` | `32-36` | `700` | Start screen headline |
| `title-1` | `24-28` | `700` | Section titles, panel headings |
| `title-2` | `18-20` | `600-700` | Card titles, mode labels |
| `body` | `15-17` | `400-500` | Primary explanatory text |
| `caption` | `12-13` | `500-600` | Status labels, helper copy |

### Spacing Variables

| Token | Value | Usage |
|-------|-------|-------|
| `--space-xs` | `4px` | Tight icon gaps |
| `--space-sm` | `8px` | Inline spacing |
| `--space-md` | `12px` | Compact control padding |
| `--space-lg` | `16px` | Standard card padding |
| `--space-xl` | `24px` | Floating panel spacing |
| `--space-2xl` | `32px` | Section separation |
| `--space-3xl` | `40px` | Start screen bottom padding |

### Radius

| Token | Value | Usage |
|------|-------|-------|
| `--radius-sm` | `10px` | Chips, compact buttons |
| `--radius-md` | `16px` | Primary buttons, cards |
| `--radius-lg` | `22px` | Floating panels, large cards |
| `--radius-xl` | `28px` | Hero panels, sheet-style modules |

### Shadow Depths

| Level | Value | Usage |
|-------|-------|-------|
| `--shadow-sm` | `0 4px 10px rgba(3, 105, 161, 0.08)` | Small controls |
| `--shadow-md` | `0 10px 24px rgba(3, 105, 161, 0.10)` | Cards, floating menu |
| `--shadow-lg` | `0 18px 40px rgba(3, 105, 161, 0.14)` | Primary panels |

### Motion

- Default transition: `180-220ms`
- Use fade + slight lift, never dramatic bounce
- Real-time camera UI must feel stable, not animated for show
- Privacy mode changes should read as deliberate and reassuring

---

## Experience Principles

### 1. Camera Preview Is The Hero

- The camera feed is the product.
- Interface chrome must float above it, not compete with it.
- Important controls should be large, obvious, and sparse.

### 2. Privacy Must Feel Visible

- Users should instantly understand whether privacy protection is active.
- `Avatar` must read as the default, recommended mode.
- `Blur` is a valid fallback, but should feel secondary.
- `Off` must exist, but never look like the recommended path.

### 3. Owner vs Bystander Must Feel Fair

- The streamer should feel protected from accidental mistakes.
- Bystanders should feel respected, not treated like threats.
- Avoid militaristic or surveillance wording such as "target", "scan", or "capture subject".

### 4. Trust Beats Hype

- Use proof, clarity, and status signals over hype gradients and flashy effects.
- If an effect does not help clarity, remove it.

---

## Core Screen Direction

### Start Screen

- Use a strong full-screen visual or blurred live-style backdrop.
- Apply cool blue overlay to create trust and focus.
- Headline should clearly communicate streamer safety + bystander comfort.
- One primary CTA only.
- Supporting copy should stay under 2 short lines.

### Live Camera Screen

- Keep the preview as the largest visual layer.
- Use floating controls with soft white or dark translucent surfaces.
- Main CTA sits in a predictable anchored position.
- Session state must be readable at a glance: ready, live, protected, stopped.

### Owner Management

- Cards should feel human and personal, not database-like.
- Use clean avatar cards with soft gradients or calm photo placeholders.
- Destructive actions must be visually quiet but discoverable.

### Privacy Mode Selector

- Present `Avatar`, `Blur`, `Off` as three explicit choices.
- Each mode needs a short helper description, not just a label.
- Selected mode should use clear outline + fill + icon change.
- Default recommended mode: `Avatar`

### Menu / Side Panel

- Use white surface, blue text hierarchy, generous spacing.
- Sections should feel administrative but still friendly.
- No pure black text on white; stay in deep blue family for consistency.

---

## Component Specs

### Primary Button

- Background: `#22C55E`
- Text: white
- Height: `52-56px`
- Radius: `16px` or capsule for main session action
- Meaning: ready / safe / confirm / proceed

### Secondary Button

- Background: white or translucent surface
- Text: `#0369A1`
- Border: `1px solid #D7EAF5`
- Meaning: alternate action, open detail, not the main path

### Tertiary / Quiet Action

- Text only or low-emphasis ghost button
- Use for dismiss, edit, remove, back, menu utilities
- Never compete visually with session CTA

### Status Chips

| State | Color | Meaning |
|------|-------|---------|
| `Avatar` | `#0369A1` + soft blue fill | Recommended protection mode |
| `Blur` | `#0EA5E9` + pale cyan fill | Softer privacy fallback |
| `Off` | `#94A3B8` + neutral fill | Explicitly inactive |
| `Live` | `#22C55E` | Safe active session |
| `Warning` | `#F59E0B` | Needs attention |
| `Error` | `#DC2626` | Failure or blocked state |

### Cards

- Background: white
- Radius: `22px`
- Border: optional `1px` soft blue line
- Shadow: `--shadow-md`
- Use for: owner cards, privacy mode cards, trust metrics, explanation panels

### Floating Overlay Panels

- Surface: `rgba(255,255,255,0.92)` or near-white solid
- Backdrop blur allowed, but subtle
- Radius: `22-28px`
- Keep contents concise
- Use layered depth, not thick borders

### Icons

- Use SF Symbols or a single consistent line/solid icon system
- Prefer: shield, eye.slash, person.crop, sparkles, camera, waveform
- Avoid: playful emoji-like or meme-like symbolism

---

## Content Rules

### Good Messaging

- "배경 인물을 자동으로 보호"
- "방송은 계속, 프라이버시는 안전하게"
- "스트리머와 시민 모두를 위한 라이브 환경"
- "아바타 치환으로 초상권 부담 줄이기"

### Avoid Messaging

- "감시"
- "타겟 추적"
- "얼굴 포획"
- "강력 차단"
- overly technical ML jargon in primary UI

### Copy Style

- Short and declarative
- Calm and respectful
- Safety-first, never fear-first
- Explain benefit before feature

---

## Layout Patterns

### If Building Marketing / Intro Surfaces

Use this order:

1. Hero with product promise
2. Problem: accidental bystander exposure in live streams
3. Solution: real-time detection + avatar replacement
4. Mode explanation: Avatar / Blur / Off
5. Trust section: creator reassurance, public comfort, ethical positioning
6. CTA

### If Building In-App Surfaces

Use this order:

1. Camera or session state
2. Current privacy mode
3. Main action
4. Contextual management tools
5. Secondary settings

---

## Anti-Patterns (Do NOT Use)

- Do not use purple gaming gradients as the default brand expression
- Do not use neon cyberpunk styling
- Do not make the app feel like surveillance software
- Do not make `Off` visually equal to `Avatar`
- Do not bury current privacy status behind menus
- Do not cover the camera preview with heavy UI chrome
- Do not use red as the dominant action color
- Do not overload the screen with analytics or debug-like boxes
- Do not use playful pink/orange creator-economy aesthetics
- Do not rely on hype copy instead of clear reassurance

---

## Pre-Delivery Checklist

- [ ] Camera preview remains the visual priority
- [ ] Current privacy mode is obvious without extra taps
- [ ] `Avatar` is the clearest recommended option
- [ ] `Off` is available but visually de-emphasized
- [ ] Streamer benefit and public benefit are both communicated
- [ ] Blue trust palette leads, green confirms
- [ ] Text contrast meets accessibility needs
- [ ] Motion is subtle and stability-first
- [ ] UI feels like a creator safety tool, not a social app
- [ ] Portrait start screen and landscape control flow both feel intentional

---

## Source Notes

This Master file is **manually synthesized** from:

- UI/UX Pro Max style references: `Swiss Modernism 2.0`, `Trust & Authority`, `Social Proof-Focused`
- UI/UX Pro Max color reference: trust/security palette close to `Insurance Platform`
- UI/UX Pro Max typography reference: `Corporate Trust`
- Product-specific constraints from the Focus app itself: live streamer target, bystander portrait-rights protection, avatar-first privacy mode
