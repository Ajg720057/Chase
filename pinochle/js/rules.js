// ---------------------------------------------------------------------------
// House rules. Everything about scoring and bidding lives here so it can be
// changed in one place. A copy of these settings is saved into each game when
// it is created, so all three phones always play by the same rules.
// ---------------------------------------------------------------------------

export const DEFAULT_RULES = {
  // First player to reach this total at the end of a hand wins.
  // If two or more pass it on the same hand, the highest total wins.
  targetScore: 1500,

  // Bidding
  minBid: 250,
  bidIncrement: 10,
  // What happens if all three players pass:
  //   'dealerStuck' - the dealer is forced to take it at the minimum bid
  //   'redeal'      - throw the hand in and the next player deals
  allPassAction: 'dealerStuck',

  // Points for each card taken in tricks (9-A deck, two of each card).
  // Default is the common "counters" count: A, 10 and K are 10 each.
  // Classic count would be { A: 11, T: 10, K: 4, Q: 3, J: 2, 9: 0 }.
  cardPoints: { A: 10, T: 10, K: 10, Q: 0, J: 0, 9: 0 },
  lastTrickBonus: 10,

  // Meld values
  meld: {
    run: 150, // A-10-K-Q-J of trump
    doubleRun: 1500,
    royalMarriage: 40, // K-Q of trump (only counted if not used in a run)
    marriage: 20, // K-Q of any other suit
    dix: 10, // 9 of trump, each
    pinochle: 40, // Q of spades + J of diamonds
    doublePinochle: 300,
    acesAround: 100,
    doubleAces: 1000,
    kingsAround: 80,
    doubleKings: 800,
    queensAround: 60,
    doubleQueens: 600,
    jacksAround: 40,
    doubleJacks: 400,
  },

  // Trick play
  // Trump can't be led until it has been "broken": someone played trump
  // because they couldn't follow suit. A player holding nothing but trump
  // may always lead it.
  trumpMustBeBroken: true,
  mustHead: true, // must beat the winning card when following suit, if able
  mustTrump: true, // if void in the led suit, must play trump if able
  mustOvertrump: true, // when trumping, must beat a trump already played, if able

  // Scoring
  meldRequiresTrick: true, // a player's meld only counts if they win a trick
  buriedPointsToBidder: true, // points in the 3 buried cards count for bidder
  bidderMadeScores: 'total', // 'total' = meld + tricks, 'bid' = just the bid
  setPenalty: 'bid', // bidder who goes set loses this: 'bid' or 'doubleBid'

  // Throwing in the hand after seeing the kitty and meld
  allowConcede: true,
  concedeOpponentsGetMeld: true,
};

export function mergeRules(overrides = {}) {
  return {
    ...DEFAULT_RULES,
    ...overrides,
    cardPoints: { ...DEFAULT_RULES.cardPoints, ...(overrides.cardPoints || {}) },
    meld: { ...DEFAULT_RULES.meld, ...(overrides.meld || {}) },
  };
}
