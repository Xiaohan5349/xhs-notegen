package com.xiaohan.xhsnotegen.domain

/** English prompt sets, used when a mode's note language is English. */
internal object EnglishPrompts {

    // ---- Food ----

    val food = PromptSet(
        heading = "This meal",
        fields = mapOf(
            FieldSlot.SUBJECT to PromptField("Dishes", "braised pork belly, sweet and sour ribs"),
            FieldSlot.PLACE to PromptField("Restaurant", "Restaurant name"),
            FieldSlot.FEELING to PromptField("Taste", "soup a bit salty, noodles really chewy"),
            FieldSlot.COST to PromptField("Price", "about ${'$'}40 for two"),
            FieldSlot.SCENE to PromptField("Setting", "waited 40 min, very loud inside"),
            FieldSlot.OTHER to PromptField("Anything else", "who with, why here, what to order next time"),
        ),
        instructions = """
You're helping me write my own food diary. I'll post it to Xiaohongshu (RedNote), but it's my diary first: written for me, and for whoever happens to scroll past. It should read like I typed it on my phone right after the meal. Nobody should be able to tell an AI wrote it.

## What real quick notes look like
- Incomplete. I don't review every dish. Usually I remember one or two things: the soup was a bit salty, we waited forty minutes, the owner threw in an extra spoon of chili oil.
- Small concrete things feel truer than general verdicts. "The beef was sliced really thick, took forever to chew" sounds like a person. "The beef had a satisfying texture" doesn't.
- Sentence length is uneven. Fragments are fine, so is casual filler (kinda, honestly, a bit, pretty, I guess, actually).
- Opinions are personal and can be vague or mixed: "Good, but kinda pricey." "Can't say what it is, but I'd come back."
- The ending can just stop on a detail. No wrap-up, no life lesson.
- Little info means a short note. Don't pad.

## What screams AI, so don't
- Formulaic openings: "Today I visited…", "My friend and I decided to try…", "Let me tell you about…"
- Summary or uplift endings: Overall, All in all, In conclusion, a must-try, worth every penny, will definitely be back, 10/10 would recommend, left me happy and full
- Tricolons and parallelism, "not only… but also…", "Firstly… Secondly… Finally…"
- Food-critic vocabulary: melt-in-your-mouth, perfectly balanced, burst of flavors, symphony of flavors, a feast for the senses, tantalizing, delectable, mouthwatering, cooked to perfection, crispy on the outside and tender on the inside, comfort food for the soul
- Marketing talk: hidden gem, nestled in, vibrant, elevate, delightful, game changer, obsessed, run don't walk, you guys, besties, highly recommend
- One neat verdict per dish, paragraphs all the same length (except in the list style)
- Emoji, strings of exclamation marks

## Facts
Only write what I gave you and what's actually visible in the photos. No price given, no price mentioned. I didn't say there was a line, so don't write one. Don't invent ingredients, cooking methods, the restaurant's backstory or the service. If something in a photo is unclear, don't guess.
Text marked "(my words)" sounds most like me: keep my phrasing and word choice, smooth it a little at most, don't make it formal.

## Title
At most 60 characters. Like a diary subtitle, plain and specific, e.g. "Friday night BBQ", "The new rice noodle place downstairs", "Back for the eel rice again". No "|", no clickbait, no exclamation marks.

## Hashtags
3 to 5, without #. Specific and useful: restaurant name, dish names, city or neighborhood, e.g. "Shanghai food", "xiaolongbao". Few generic ones like "foodie".

## Warnings
If my info and the photos don't match (say I wrote hot pot and the photos show sushi), or a fact is uncertain, put one short English sentence in warnings. Otherwise return an empty array.

## Tone examples (learn the tone and rhythm, don't copy the content)
Example 1:
Sat, Mar 8 · lunch
Waited almost forty minutes, nearly gave up.
The crab roe noodles really are good, but it gets a bit rich toward the end. Add the vinegar on the table.
Also got a lion's head meatball, looser than I expected, fell apart the second I touched it.
A bit over ${'$'}40 for two, not bad for the area.

Example 2:
Thu, Nov 2 · dinner
Too tired after work, just grabbed beef noodle soup downstairs.
Broth was lighter than last time, same three slices of beef.
Still, something hot. I feel human again.
""".trimIndent(),
        styles = mapOf(
            NoteStyle.CASUAL_STORY.key to "Casual note: like texting a friend about this meal — how we ended up there, who with, the one or two things that stuck. 60 to 150 words, a few short paragraphs.",
            NoteStyle.PRACTICAL.key to "Practical note: for future me to look up. What we ordered, how each was (a plain sentence or two), what it cost, would I come back. Line breaks are fine, but every line is a full spoken sentence, not a label. 70 to 150 words.",
            NoteStyle.PUNCHY.key to "List style: a few short lines, each starting with a small label (like Ordered / Taste / Price / Setting / Next time — only labels that have info), then one plain sentence, no pile of adjectives. Can end with one aside of my own.",
            NoteStyle.CLEAN.key to "Minimal: besides the date line, only two to four sentences. What I ate, plus the single most concrete impression.",
        ),
    )

    // ---- Travel ----

    val travel = PromptSet(
        heading = "This trip",
        fields = mapOf(
            FieldSlot.SUBJECT to PromptField("What I did", "hiked Fushimi Inari, walked Nishiki Market"),
            FieldSlot.PLACE to PromptField("Place", "Place, sight or trip name"),
            FieldSlot.FEELING to PromptField("How it felt", "way too crowded, but quiet at the top"),
            FieldSlot.COST to PromptField("Cost", "free entry, ${'$'}12 taxi"),
            FieldSlot.SCENE to PromptField("Weather & crowds", "light rain, quiet in the morning"),
            FieldSlot.OTHER to PromptField("Anything else", "who with, how we got there, anything funny that happened"),
        ),
        instructions = """
You're helping me write my own travel diary. I'll post it to Xiaohongshu (RedNote), but it's my diary first: written for me, and for whoever happens to scroll past. It should read like I typed it on my phone in the hotel at the end of the day. Nobody should be able to tell an AI wrote it.

## What real quick notes look like
- Incomplete. I don't walk through every place we went. Usually I remember a moment or two: more steps than I expected, rain on the way down, a cat by the road.
- Small concrete things feel truer than general verdicts. "Halfway up my legs started shaking" sounds like a person. "The hike was an amazing experience" doesn't.
- Sentence length is uneven. Fragments are fine, so is casual filler (kinda, honestly, a bit, pretty, I guess, actually).
- Feelings are personal and can be vague or mixed: "So crowded I wanted to leave, but the photos came out great."
- The ending can just stop on an image. No wrap-up, no life lesson.
- Little info means a short note. Don't pad.

## What screams AI, so don't
- Itinerary-style openings: "Today I visited…", "On day two of our trip we headed to…", "Welcome to beautiful…"
- Summary or uplift endings: Overall, All in all, In conclusion, a must-visit, worth the trip, will definitely be back, it healed my soul, found my inner peace, memories to last a lifetime
- Tricolons and parallelism, "not only… but also…", "Firstly… Secondly… Finally…"
- Travel-brochure vocabulary: breathtaking, picturesque, paradise, stunning views, hidden gem, off the beaten path, a step back in time, steeped in history, nestled in, vibrant, bucket list, wanderlust
- Marketing talk: elevate, delightful, iconic, a must, you guys, besties, highly recommend, run don't walk
- One neat verdict per sight, paragraphs all the same length (except in the list style)
- Emoji, strings of exclamation marks

## Facts
Only write what I gave you and what's actually visible in the photos. No price given, no price mentioned. I didn't say there was a line, so don't write one. Don't invent a place's history, legends, opening hours or routes. If something in a photo is unclear, don't guess.
Text marked "(my words)" sounds most like me: keep my phrasing and word choice, smooth it a little at most, don't make it formal.

## Title
At most 60 characters. Like a diary subtitle, plain and specific, e.g. "Fushimi Inari in the rain", "First time at the beach alone". No "|", no clickbait, no exclamation marks.

## Hashtags
3 to 5, without #. Specific and useful: city, sight names, what we did, e.g. "Kyoto travel", "Fushimi Inari". Few generic ones like "travel".

## Warnings
If my info and the photos don't match, or a fact is uncertain, put one short English sentence in warnings. Otherwise return an empty array.

## Tone examples (learn the tone and rhythm, don't copy the content)
Example 1:
Sat, Apr 12 · morning
Left at seven hoping to beat the crowds. There was already a line when we got there.
The gates just keep going up. Halfway my legs started shaking, but past that there was almost nobody, and honestly that part was the prettiest.
Light rain on the way down, so we hid outside a little shop and ate dango.

Example 2:
Sun, Aug 3 · evening
Last-minute beach trip, an hour and a half on the bus.
Super windy, my hair kept slapping my face.
For the few minutes the sun went down everyone kinda went quiet. That was nice.
""".trimIndent(),
        styles = mapOf(
            NoteStyle.CASUAL_STORY.key to "Casual note: like telling a friend about the day — how we got there, who with, the one or two moments that stuck. 60 to 150 words, a few short paragraphs.",
            NoteStyle.PRACTICAL.key to "Practical note: for future me and anyone thinking of going. How to get there, what it cost, what time is good, worth a special trip or not. Line breaks are fine, but every line is a full spoken sentence. 70 to 150 words.",
            NoteStyle.PUNCHY.key to "List style: a few short lines, each starting with a small label (like Went to / Getting there / Cost / Crowds / Tip — only labels that have info), then one plain sentence. Can end with one aside of my own.",
            NoteStyle.CLEAN.key to "Minimal: besides the date line, only two to four sentences. Where I went, plus the single most concrete image or feeling.",
        ),
    )

    // ---- General modes for Xiaohongshu's biggest categories ----

    val outfit = englishCategory(
        heading = "Today's outfit",
        subject = PromptField("Outfit", "beige trench, straight-leg jeans, loafers"),
        place = PromptField("Occasion", "weekend shopping, work commute"),
        feeling = PromptField("How it felt", "trench makes me look shorter, but it's warm"),
        cost = PromptField("Price & brand", "trench from Uniqlo, jeans I've had for three years"),
        scene = PromptField("Size & weather", "5'4\", 110 lb, size M; 60°F and windy"),
        spec = EnglishCategorySpec(
            diary = "outfit diary", moment = "snapped a mirror pic before heading out and jotted down what I'm wearing",
            smallDetails = "sleeves a bit long so I rolled them twice, the hem looked nice when the wind caught it, shoes rubbed after a full day of walking",
            concrete = "\"The waist hits right at the narrowest part\" sounds like a person; \"super flattering fit\" doesn't.",
            cliches = "effortlessly chic, elevated basics, instantly slimming, makes your legs look a mile long, a wardrobe staple, versatile, looks good on everyone, obsessed, just buy it",
            facts = "Don't invent fabric, brand, price or size; if I didn't say it, leave it out.",
            titles = "\"First trench coat of the cold season\", \"The jeans I wore to work all week\"",
            tags = "\"work outfit\", \"petite style\", item names",
            example1 = "Wed, Oct 8 · morning\nGot cold, so I dug out last year's beige trench.\nWith the straight jeans it made me look kinda short. Switched to loafers with a bit of heel, better.\nA coworker said I looked like I was off on a business trip.",
            example2 = "Mon, May 20\nThe new white tee already shrank a bit after one wash.\nWore it anyway, with a shirt over it you can't tell.",
        ),
    )

    val beauty = englishCategory(
        heading = "What I used",
        subject = PromptField("Products", "a serum, a foundation in shade 02"),
        place = PromptField("Routine", "morning and night skincare, work makeup"),
        feeling = PromptField("How it worked", "not sticky, but T-zone gets oily by afternoon"),
        cost = PromptField("Price & where bought", "${'$'}65 at the counter, used for two weeks"),
        scene = PromptField("Skin type & season", "combination-oily, gets red when seasons change"),
        spec = EnglishCategorySpec(
            diary = "beauty and skincare diary", moment = "took off my makeup, washed my face and jotted down what I've been using lately",
            smallDetails = "the pump spills easily, the scent is a bit strong, a pimple showed up on my chin on day three",
            concrete = "\"By 3pm it starts clinging to the sides of my nose\" sounds like a person; \"longevity is average\" doesn't.",
            cliches = "holy grail, game changer, glass skin, lit from within, flawless finish, a must-have, repurchase forever, dupe of the year, melts into skin, skin looks ten years younger",
            facts = "Don't invent ingredients, effects, price or how long I've used it. No exaggerated or medical claims (clears acne, brightens by X shades, anti-aging results); if I only said how it felt, only write how it felt.",
            titles = "\"What I used the two weeks my skin was red\", \"I finished this foundation\"",
            tags = "product names, \"combination skin\", \"everyday makeup\"",
            example1 = "Sat, Mar 2 · evening\nBeen using this serum for almost a month.\nSinks in fast, but the scent is kinda strong, took me a week to get used to it.\nRedness seems a bit better, or maybe it's just warmer out.",
            example2 = "Fri, Nov 15\nThe new foundation in 02 is still a bit light for me.\nMixed it with my old one. Still clings around my nose by the afternoon.",
        ),
    )

    val home = englishCategory(
        heading = "At home",
        subject = PromptField("What changed", "new curtains, a floor lamp"),
        place = PromptField("Room", "living room, rented bedroom"),
        feeling = PromptField("How it is", "warm light at night, but the switch is awkward"),
        cost = PromptField("Cost & where from", "lamp ${'$'}30, custom curtains ${'$'}90"),
        scene = PromptField("Size & before/after", "130 sq ft, used to be really dark"),
        spec = EnglishCategorySpec(
            diary = "home diary", moment = "finished tidying up, sat down on the couch and jotted this down",
            smallDetails = "only after installing it did I notice the cabinet door blocks the switch, the cat claimed the new cushion on day one, the delivery boxes are still in the hall",
            concrete = "\"With this lamp on at night it feels like a café\" sounds like a person; \"such a cozy vibe\" doesn't.",
            cliches = "cozy vibes, my little sanctuary, rental glow-up, budget-friendly makeover, aesthetic, elevate your space, home must-haves, instant serotonin",
            facts = "Don't invent sizes, prices, brands or materials; if I didn't say it, leave it out.",
            titles = "\"Finally changed the living room curtains\", \"First floor lamp in the rental\"",
            tags = "\"rental makeover\", room names, item names",
            example1 = "Sun, Jun 9\nPut it off for half a year, finally swapped the curtains.\nBlocks the light way better, the sun doesn't wake me up anymore.\nColor's a bit darker than online. It's fine.",
            example2 = "Tue, Jan 14 · evening\nFloor lamp arrived, took me twenty minutes to put together.\nThe switch is on the cord so I have to bend down to press it. Kinda annoying.",
        ),
    )

    val fitness = englishCategory(
        heading = "This workout",
        subject = PromptField("Workout", "ran 5K, hiked the hill trail"),
        place = PromptField("Place", "neighborhood track, the gym, a mountain"),
        feeling = PromptField("How it felt", "legs got heavy from the third kilometer"),
        cost = PromptField("Time & distance", "38 min, 7:30 per km"),
        scene = PromptField("Weather & gear", "humid, first time in new running shoes"),
        spec = EnglishCategorySpec(
            diary = "workout diary", moment = "just finished, still catching my breath, and jotted this down on my phone",
            smallDetails = "earbuds died so I could only hear myself panting, shoelace came undone halfway, knees hurt a bit on the way down",
            concrete = "\"Gritted my teeth through the last kilometer\" sounds like a person; \"pushed past my limits\" doesn't.",
            cliches = "no pain no gain, discipline equals freedom, crushed it, beast mode, torched calories, pushed past my limits, sweat never lies, day N of my journey, fitness journey, just do it",
            facts = "Don't invent distance, pace, heart rate, calories burned or exercises. Don't give health or weight-loss advice.",
            titles = "\"A 5K before the rain\", \"First time up the mountain\"",
            tags = "the sport, the place, \"running diary\"",
            example1 = "Thu, Jul 18 · morning\nOut at six and it was still muggy.\nLegs got heavy from the third kilometer, the last one was mostly walk-run.\nDrank a whole bottle of water when I got back.",
            example2 = "Wed, Oct 2\nHiked the mountain, way more people than I expected.\nKnees hurt a bit going down. Bringing knee braces next time.",
        ),
    )

    val parenting = englishCategory(
        heading = "Today",
        subject = PromptField("What happened", "fed herself for the first time, got vaccinated"),
        place = PromptField("Place", "at home, the park nearby"),
        feeling = PromptField("How it went", "food all over her face, but so focused"),
        cost = PromptField("Age", "14 months"),
        scene = PromptField("Things we used", "a suction bowl"),
        spec = EnglishCategorySpec(
            diary = "parenting diary", moment = "the kid finally fell asleep and I jotted down the little things from today",
            smallDetails = "held the spoon upside down and was very proud of it, cried for three seconds after the shot then went to look at a dog, napped for only forty minutes",
            concrete = "\"Handed me the spoon so I could have a bite too\" sounds like a person; \"so thoughtful\" doesn't.",
            cliches = "tiny human, little angel, mom hack, parenting win, every mom needs this, must-have for new moms, my heart is so full, cherish every moment, they grow up so fast",
            facts = "Don't invent the child's development, or give medical or parenting advice. Never write the child's full name, school, address or other private details.",
            titles = "\"First time holding the spoon herself\", \"Vaccine day\"",
            tags = "age in months, \"baby food\", \"toddler life\"",
            example1 = "Thu, Apr 3 · evening\nFirst time feeding herself with a spoon today.\nHalf went in her mouth, half on the table, some in her hair.\nBut she was so serious about it, wouldn't let me help.",
            example2 = "Fri, Sep 12\nVaccine day. Cried for about three seconds.\nThen saw the neighbor's dog and forgot all about it. Kept barking at home the rest of the day.",
        ),
    )

    val booksFilms = englishCategory(
        heading = "What I read / watched",
        subject = PromptField("Title", "a book, a film, an exhibition"),
        place = PromptField("Where / how", "cinema, Kindle, the art museum"),
        feeling = PromptField("What stuck", "cried at the last scene"),
        cost = PromptField("Tickets / price", "movie ticket ${'$'}14"),
        scene = PromptField("Favorite line or part", "one line I keep thinking about"),
        spec = EnglishCategorySpec(
            diary = "reading and watching diary", moment = "just finished it and jotted this down while it's still fresh",
            smallDetails = "zoned out and checked my phone in the middle, the person next to me kept eating popcorn, screenshotted one line",
            concrete = "\"The dinner scene at the end made me want to call home\" sounds like a person; \"deeply moving\" doesn't.",
            cliches = "masterpiece, a must-watch, a must-read, instant classic, it stays with you, emotional rollercoaster, couldn't put it down, left me speechless, a tour de force, cried my eyes out",
            facts = "Don't invent plot, author, director, cast or ratings. No spoilers beyond what I mentioned.",
            titles = "\"The film I saw this weekend kinda lingers\", \"Halfway through this book\"",
            tags = "book or film title, \"reading notes\", \"movie diary\"",
            example1 = "Sat, Feb 24 · evening\nWent alone, only seven or eight people in the theater.\nThe first half is slow, almost fell asleep.\nThe dinner at the end made me want to call home.",
            example2 = "Wed, Aug 7\nHalfway through this book.\nSome chapters drag, but I read the part about the grandma twice.",
        ),
    )

    // ---- Custom modes ----

    /** Starting point for new custom modes. */
    val generic = PromptSet(
        heading = "This time",
        fields = FieldSlot.entries.associateWith { genericField(it) },
        instructions = """
You're helping me write my own diary. I'll post it to Xiaohongshu (RedNote), but it's my diary first: written for me, and for whoever happens to scroll past. It should read like I typed it on my phone. Nobody should be able to tell an AI wrote it.

## What real quick notes look like
- Incomplete. Usually I remember one or two concrete details.
- Small concrete things feel truer than general verdicts.
- Sentence length is uneven. Fragments are fine, so is casual filler (kinda, honestly, a bit, pretty, actually).
- Feelings can be mixed, good and bad both.
- The ending can just stop on a detail. No wrap-up, no life lesson.
- Little info means a short note. Don't pad.

## What screams AI, so don't
- Formulaic openings: "Today I…", "Let me share…"
- Summary or uplift endings: Overall, All in all, a must-try, worth every penny, 10/10 would recommend
- Tricolons and parallelism, "not only… but also…", "Firstly… Secondly… Finally…"
- Marketing talk: hidden gem, nestled in, vibrant, elevate, delightful, game changer, you guys, besties, highly recommend
- Emoji, strings of exclamation marks

## Facts
Only write what I gave you and what's actually visible in the photos. Don't make anything up. Text marked "(my words)" should keep my phrasing.

## Title
At most 60 characters. Plain and specific like a diary subtitle. No "|", no clickbait, no exclamation marks.

## Hashtags
3 to 5, without #. Specific and useful, few generic ones.

## Warnings
If my info and the photos don't match, or a fact is uncertain, put one short English sentence in warnings. Otherwise return an empty array.
""".trimIndent(),
        styles = NoteStyle.entries.associate { it.key to genericStyle(it) },
    )

    fun genericStyle(style: NoteStyle): String = when (style) {
        NoteStyle.CASUAL_STORY -> "Casual note: like texting a friend about it, the one or two details that stuck. 60 to 150 words, a few short paragraphs."
        NoteStyle.PRACTICAL -> "Practical note: for future me to look up. Key info, what it cost, worth it or not. Line breaks are fine, but every line is a full spoken sentence. 70 to 150 words."
        NoteStyle.PUNCHY -> "List style: a few short lines, each starting with a small label, only labels that have info, then one plain sentence. Can end with one aside of my own."
        NoteStyle.CLEAN -> "Minimal: besides the date line, only two to four sentences, keeping just the single most concrete detail."
    }

    fun genericField(slot: FieldSlot): PromptField = when (slot) {
        FieldSlot.SUBJECT -> PromptField("What", "")
        FieldSlot.PLACE -> PromptField("Where", "")
        FieldSlot.FEELING -> PromptField("How it felt", "")
        FieldSlot.COST -> PromptField("Cost", "")
        FieldSlot.SCENE -> PromptField("Details", "")
        FieldSlot.OTHER -> PromptField("Anything else", "")
    }
}

/** What differs between the English category prompts; the rest is shared. */
private data class EnglishCategorySpec(
    val diary: String,          // "outfit diary"
    val moment: String,         // when you'd jot it down
    val smallDetails: String,   // examples of small, real details
    val concrete: String,       // a concrete-vs-vague example pair
    val cliches: String,        // this category's typical AI / marketing phrases
    val facts: String,          // what must not be invented
    val titles: String,
    val tags: String,
    val example1: String,
    val example2: String,
)

private fun englishCategory(
    heading: String,
    subject: PromptField, place: PromptField, feeling: PromptField, cost: PromptField, scene: PromptField,
    spec: EnglishCategorySpec,
) = PromptSet(
    heading = heading,
    fields = mapOf(
        FieldSlot.SUBJECT to subject, FieldSlot.PLACE to place, FieldSlot.FEELING to feeling,
        FieldSlot.COST to cost, FieldSlot.SCENE to scene,
        FieldSlot.OTHER to PromptField("Anything else", ""),
    ),
    instructions = englishCategoryPrompt(spec),
    styles = NoteStyle.entries.associate { it.key to EnglishPrompts.genericStyle(it) },
)

private fun englishCategoryPrompt(s: EnglishCategorySpec) = """
You're helping me write my own ${s.diary}. I'll post it to Xiaohongshu (RedNote), but it's my diary first: written for me, and for whoever happens to scroll past. It should read like I just ${s.moment}. Nobody should be able to tell an AI wrote it.

## What real quick notes look like
- Incomplete. I don't cover everything. Usually I remember one or two small details: ${s.smallDetails}.
- Small concrete things feel truer than general verdicts. ${s.concrete}
- Sentence length is uneven. Fragments are fine, so is casual filler (kinda, honestly, a bit, pretty, I guess, actually).
- Feelings are personal and can be vague or mixed. Good and bad both belong.
- The ending can just stop on a detail. No wrap-up, no life lesson.
- Little info means a short note. Don't pad.

## What screams AI, so don't
- Formulaic openings: "Today I…", "Today I want to share…", "Let me tell you about…"
- Summary or uplift endings: Overall, All in all, In conclusion, a must-try, worth every penny, 10/10 would recommend, highly recommend
- Tricolons and parallelism, "not only… but also…", "Firstly… Secondly… Finally…"
- Phrases this kind of post is full of: ${s.cliches}
- General marketing talk: hidden gem, vibrant, elevate, delightful
- Addressing readers: you guys, besties, girlies
- Emoji, strings of exclamation marks

## Facts
Only write what I gave you and what's actually visible in the photos. ${s.facts} If something in a photo is unclear, don't guess.
Text marked "(my words)" sounds most like me: keep my phrasing and word choice, smooth it a little at most, don't make it formal.

## Title
At most 60 characters. Like a diary subtitle, plain and specific, e.g. ${s.titles}. No "|", no clickbait, no exclamation marks.

## Hashtags
3 to 5, without #. Specific and useful, e.g. ${s.tags}. Few generic ones.

## Warnings
If my info and the photos don't match, or a fact is uncertain, put one short English sentence in warnings. Otherwise return an empty array.

## Tone examples (learn the tone and rhythm, don't copy the content)
Example 1:
${s.example1}

Example 2:
${s.example2}
""".trimIndent()
