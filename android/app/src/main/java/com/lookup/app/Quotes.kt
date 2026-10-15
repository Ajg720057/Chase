package com.lookup.app

data class Quote(val text: String, val author: String)

/** Bundled with the app so it works offline and never needs network access. */
object Quotes {
    val ALL = listOf(
        Quote("How we spend our days is, of course, how we spend our lives.", "Annie Dillard"),
        Quote("It is not that we have a short time to live, but that we waste a lot of it.", "Seneca"),
        Quote("Life is long if you know how to use it.", "Seneca"),
        Quote("Begin at once to live, and count each separate day as a separate life.", "Seneca"),
        Quote("Waste no more time arguing what a good man should be. Be one.", "Marcus Aurelius"),
        Quote("Almost everything will work again if you unplug it for a few minutes, including you.", "Anne Lamott"),
        Quote("Lost time is never found again.", "Benjamin Franklin"),
        Quote("Well done is better than well said.", "Benjamin Franklin"),
        Quote("Time is what we want most, but what we use worst.", "William Penn"),
        Quote("You will never find time for anything. If you want time you must make it.", "Charles Buxton"),
        Quote("Until we can manage time, we can manage nothing else.", "Peter Drucker"),
        Quote("The key is not to prioritize what's on your schedule, but to schedule your priorities.", "Stephen Covey"),
        Quote("Wherever you are, be all there.", "Jim Elliot"),
        Quote("Don't count the days, make the days count.", "Muhammad Ali"),
        Quote("The way to get started is to quit talking and begin doing.", "Walt Disney"),
        Quote("Either you run the day, or the day runs you.", "Jim Rohn"),
        Quote("The bad news is time flies. The good news is you're the pilot.", "Michael Altshuler"),
        Quote("Simplicity boils down to two steps: identify the essential, eliminate the rest.", "Leo Babauta"),
        Quote("Focus on being productive instead of busy.", "Tim Ferriss"),
        Quote("The successful warrior is the average man, with laser-like focus.", "Bruce Lee"),
        Quote("If you spend too much time thinking about a thing, you'll never get it done.", "Bruce Lee"),
        Quote("Concentrate all your thoughts upon the work at hand. The sun's rays do not burn until brought to a focus.", "Alexander Graham Bell"),
        Quote("It's not what we do once in a while that shapes our lives, but what we do consistently.", "Tony Robbins"),
        Quote("Nothing is particularly hard if you divide it into small jobs.", "Henry Ford"),
        Quote("Life is what happens when you're busy making other plans.", "John Lennon"),
        Quote("Be present in all things and thankful for all things.", "Maya Angelou"),
        Quote("You can't use up creativity. The more you use, the more you have.", "Maya Angelou"),
        Quote("Realize deeply that the present moment is all you have.", "Eckhart Tolle"),
        Quote("The present moment is filled with joy and happiness. If you are attentive, you will see it.", "Thich Nhat Hanh"),
        Quote("Distracted from distraction by distraction.", "T. S. Eliot"),
        Quote("Attention is the rarest and purest form of generosity.", "Simone Weil"),
        Quote("My experience is what I agree to attend to.", "William James"),
        Quote("What you stay focused on will grow.", "Roy T. Bennett"),
        Quote("You'll never change your life until you change something you do daily.", "John C. Maxwell"),
        Quote("Motivation is what gets you started. Habit is what keeps you going.", "Jim Ryun"),
        Quote("Small deeds done are better than great deeds planned.", "Peter Marshall"),
        Quote("Action is the foundational key to all success.", "Pablo Picasso"),
        Quote("Do the difficult things while they are easy and do the great things while they are small.", "Lao Tzu"),
        Quote("In the midst of movement and chaos, keep stillness inside of you.", "Deepak Chopra"),
        Quote("Rest is not idleness.", "John Lubbock"),
        Quote("We are what we repeatedly do.", "Will Durant"),
        Quote("Look up from your phone, shut down that display, stop watching this video, live life the real way.", "Gary Turk"),
        Quote("Discipline is choosing between what you want now and what you want most.", "Unknown"),
        Quote("Starve your distractions, feed your focus.", "Unknown"),
        Quote("One day or day one. You decide.", "Unknown"),
        Quote("The feed will still be there. This moment won't.", "Unknown"),
    )

    private var last = -1

    /** A random quote, never the same one twice in a row. */
    fun random(): Quote {
        var i: Int
        do { i = ALL.indices.random() } while (i == last && ALL.size > 1)
        last = i
        return ALL[i]
    }
}
