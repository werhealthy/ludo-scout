# Ludo Scout UX System v2 — Connected Product Graph

This phase extends UX System v1. The app is not a set of unrelated screens: listings, games and tags form a connected graph.

## Core entities

### Listing
A temporary Vinted offer. It answers:
- What is this offer?
- Is it worth attention?
- What does it cost?
- Where can I open it?
- Which canonical game does it belong to?

A listing detail must not become the canonical knowledge page for the game.

### Game
A persistent canonical entity. It answers:
- What game is this?
- What is its robust quality signal?
- Which tags/mechanics describe it?
- Which similar games exist?
- Which Vinted listings are active?
- What has the market looked like over time?

### Tag
A navigation object, not decoration.
Categories, mechanics, designers and publishers can become entry points into the game database.

## Listing detail hierarchy
Reading order:
1. gallery
2. deal signal + recency
3. title
4. Ludo Score / language / BGG
5. canonical Game link
6. tags
7. price and offer
8. Vinted primary provider action
9. BGG / Library secondary actions
10. price context and listing facts
11. bundle / accessory context when relevant

Never concatenate unrelated metadata into one long gray sentence.

## Ludo Score
Ludo Score is a robust quality signal, not the raw BGG average.
The current composite uses:
- BGG rank: 55%
- Bayesian/geek rating: 20%
- voter count: 15%
- average rating: 10%

When rank is unavailable, the score is capped to avoid overconfidence.
The UI must let the user inspect how the score is derived.

## Game detail
Game detail is the canonical hub:
- title + cover
- Ludo Score
- raw BGG metadata as supporting evidence
- clickable category/mechanic tags
- summary and creator/publisher metadata
- similar games from the local database
- market stats and price history
- active listings of this game

"Other listings of the same game" belongs here, not in listing detail.

## Connected navigation
Clicking a tag opens the Games database with that tag as the query.
Game search must match:
- title and aliases
- categories
- mechanics
- designers
- publishers
- families

## Filters
Advanced filters are a separate full-screen task, inspired by marketplace apps like Vinted:
- the market screen stays calm
- Filter and Sort are the visible entry points
- only active filters remain visible on the result screen
- advanced options live in the dedicated filter screen
- secondary management actions are placed at the end, not mixed with primary filtering

## Provider identity
Keep Vinted and BoardGameGeek logos visible where they improve recognition.
Provider branding is navigation context, not decoration.

## Spacing
Default content margin: 20–22dp.
Major sections: 24–30dp separation.
Rows: 10–12dp vertical internal padding.
Do not compress unrelated facts into a single line simply to save height.
