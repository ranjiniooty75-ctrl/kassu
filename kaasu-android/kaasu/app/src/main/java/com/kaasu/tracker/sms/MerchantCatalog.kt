package com.kaasu.tracker.sms

data class Merchant(val name: String, val category: String)

/**
 * Flexible merchant recognition. Each entry is a display name, a category and the
 * keywords that identify it inside an SMS. Add entries freely — nothing else in the
 * app is hard-coded to a particular merchant. User corrections (saved as rules in
 * the database) take priority over this list.
 */
object MerchantCatalog {

    private class Entry(val merchant: Merchant, val re: Regex)

    private fun kw(words: List<String>): Regex =
        Regex("(?<![a-z0-9])(?:" + words.joinToString("|") { Regex.escape(it) } + ")(?![a-z0-9])")

    private fun e(name: String, category: String, vararg words: String) =
        Entry(Merchant(name, category), kw(words.toList()))

    // Order matters: more specific names first, payment apps last.
    private val entries = listOf(
        e("Swiggy Instamart", "Groceries", "instamart"),
        e("Swiggy", "Food", "swiggy", "bundl technologies"),
        e("Zomato", "Food", "zomato"),
        e("Blinkit", "Groceries", "blinkit", "grofers"),
        e("Zepto", "Groceries", "zepto", "kiranakart"),
        e("BigBasket", "Groceries", "bigbasket", "bbnow", "supermarket grocery supplies"),
        e("DMart", "Groceries", "dmart", "avenue supermarts"),
        e("Amazon", "Shopping", "amazon", "amzn", "amazon.in", "amazon seller services"),
        e("Flipkart", "Shopping", "flipkart"),
        e("Myntra", "Shopping", "myntra"),
        e("Meesho", "Shopping", "meesho"),
        e("Ajio", "Shopping", "ajio"),
        e("Nykaa", "Shopping", "nykaa"),
        e("Reliance Digital", "Shopping", "reliance digital"),
        e("Croma", "Shopping", "croma"),
        e("Uber", "Travel", "uber"),
        e("Ola", "Travel", "olacabs", "ola cabs", "ani technologies"),
        e("Rapido", "Travel", "rapido", "roppen"),
        e("IRCTC", "Travel", "irctc"),
        e("redBus", "Travel", "redbus"),
        e("MakeMyTrip", "Travel", "makemytrip", "mmt"),
        e("IndiGo", "Travel", "indigo", "interglobe"),
        e("FASTag", "Travel", "fastag"),
        e("Chennai Metro", "Travel", "cmrl", "chennai metro"),
        e("BookMyShow", "Entertainment", "bookmyshow", "bigtree"),
        e("Netflix", "Entertainment", "netflix"),
        e("Spotify", "Entertainment", "spotify"),
        e("Hotstar", "Entertainment", "hotstar", "disney"),
        e("YouTube", "Entertainment", "youtube"),
        e("Google Play", "Entertainment", "google play", "play store"),
        e("PVR INOX", "Entertainment", "pvr", "inox"),
        e("Airtel", "Bills", "airtel"),
        e("Jio", "Bills", "jio", "reliance jio"),
        e("Vi", "Bills", "vodafone idea", "vodafone", "vi prepaid", "vi postpaid"),
        e("BSNL", "Bills", "bsnl"),
        e("ACT Fibernet", "Bills", "act fibernet"),
        e("TNEB", "Bills", "tneb", "tangedco", "tnpdcl"),
        e("BESCOM", "Bills", "bescom"),
        e("Tata Power", "Bills", "tata power"),
        e("LIC", "Bills", "lic", "life insurance corporation"),
        e("Indian Oil", "Fuel", "iocl", "indian oil"),
        e("HP Petrol", "Fuel", "hpcl", "hindustan petroleum"),
        e("Bharat Petroleum", "Fuel", "bpcl", "bharat petroleum"),
        e("Shell", "Fuel", "shell"),
        e("Apollo Pharmacy", "Medical", "apollo"),
        e("MedPlus", "Medical", "medplus"),
        e("PharmEasy", "Medical", "pharmeasy"),
        e("Tata 1mg", "Medical", "1mg"),
        e("Netmeds", "Medical", "netmeds"),
        e("Domino's", "Food", "dominos", "domino's", "jubilant foodworks"),
        e("McDonald's", "Food", "mcdonald", "mcdonalds"),
        e("KFC", "Food", "kfc"),
        e("Starbucks", "Food", "starbucks"),
        e("Zerodha", "Investments", "zerodha"),
        e("Groww", "Investments", "groww", "nextbillion"),
        e("Upstox", "Investments", "upstox"),
        e("CRED", "Card payment", "cred club", "dreamplug", "cred"),
        e("PhonePe", "Other", "phonepe"),
        e("Google Pay", "Other", "google pay", "gpay"),
        e("Paytm", "Other", "paytm")
    )

    /** Finds a known merchant anywhere in the (lower-cased) SMS text. */
    fun find(lowerText: String): Merchant? =
        entries.firstOrNull { it.re.containsMatchIn(lowerText) }?.merchant

    private val categoryRules: List<Pair<String, Regex>> = listOf(
        "Fuel" to kw(listOf("petrol", "fuel", "filling station", "petroleum", "diesel")),
        "Bills" to kw(listOf("electricity", "recharge", "broadband", "dth", "postpaid", "prepaid", "insurance", "gas", "water bill", "bill payment")),
        "Medical" to kw(listOf("pharmacy", "pharma", "medical", "medicals", "hospital", "clinic", "chemist", "diagnostic", "diagnostics", "lab", "labs")),
        "Travel" to kw(listOf("metro", "toll", "railway", "bus", "cab", "cabs", "airlines", "travels", "parking", "tours")),
        "Groceries" to kw(listOf("grocery", "groceries", "provision", "provisions", "vegetables", "fruits", "dairy", "milk")),
        "Food" to kw(listOf("restaurant", "cafe", "hotel", "bakery", "sweets", "foods", "kitchen", "biryani", "tea", "coffee", "mess", "tiffin")),
        "Shopping" to kw(listOf("mart", "store", "stores", "fashion", "textiles", "silks", "retail", "mall", "traders", "electronics", "enterprises")),
        "Entertainment" to kw(listOf("cinema", "cinemas", "movies", "theatre", "theater", "games", "gaming")),
        "Education" to kw(listOf("school", "college", "fees", "academy", "tuition", "university")),
        "Rent" to kw(listOf("rent", "rental"))
    )

    /** Best-guess category from a merchant name using generic keywords. */
    fun categoryFor(lowerName: String): String? =
        categoryRules.firstOrNull { it.second.containsMatchIn(lowerName) }?.first
}
