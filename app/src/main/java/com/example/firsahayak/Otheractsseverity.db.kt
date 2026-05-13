package com.example.firsahayak

/**
 * Severity database for non-BNS Indian Acts commonly appearing in FIRs.
 *
 * Covers:
 *  - Dowry Prohibition Act, 1961 (दहेज प्रतिषेध अधिनियम)
 *  - Protection of Children from Sexual Offences Act, 2012 (POCSO)
 *  - Muslim Women (Protection of Rights on Divorce) Act, 1986
 *  - Muslim Women (Protection of Rights on Marriage) Act, 2019
 *  - Scheduled Castes and Scheduled Tribes (Prevention of Atrocities) Act, 1989
 *  - Information Technology Act, 2000
 *  - Arms Act, 1959
 *  - Narcotic Drugs and Psychotropic Substances Act, 1985 (NDPS)
 *  - Unlawful Activities (Prevention) Act, 1967 (UAPA)
 *  - Protection of Women from Domestic Violence Act, 2005
 *  - Immoral Traffic (Prevention) Act, 1956
 *  - Juvenile Justice Act, 2015
 */
object OtherActsSeverityDb {

    // ── Act identifier constants (matched against act_name from LLM) ──────────
    // These are canonical short keys used internally for matching.
    // The display name comes from the LLM's act_name field.
    enum class ActType {
        DOWRY_PROHIBITION,
        POCSO,
        MUSLIM_WOMEN_DIVORCE,
        MUSLIM_WOMEN_MARRIAGE,
        SC_ST_ATROCITIES,
        IT_ACT,
        ARMS_ACT,
        NDPS,
        UAPA,
        DOMESTIC_VIOLENCE,
        IMMORAL_TRAFFIC,
        JUVENILE_JUSTICE,
        UNKNOWN
    }

    data class OtherActEntry(
        val actType       : ActType,
        val section       : String,
        val shortTitle    : String,
        val definition    : String,
        val severity      : BnsSeverityDb.SeverityLevel,
        val cognisable    : Boolean,
        val bailable      : Boolean,
        val maxPunishment : String,
        val urgencyPoints : Int
    )

    data class OtherActSeverityResult(
        val level         : BnsSeverityDb.SeverityLevel,
        val cognisable    : Boolean,
        val bailable      : Boolean,
        val urgencyScore  : Int,
        val matchedEntries: List<OtherActEntry>,
        val unmatchedSecs : List<String>
    )

    // ─────────────────────────────────────────────────────────────────────────
    // ACT NAME → ActType mapping
    // Checks if the LLM-returned act_name matches a known act.
    // Uses substring matching (case-insensitive) to handle Hindi/English variants.
    // ─────────────────────────────────────────────────────────────────────────

    private val actNamePatterns: List<Pair<ActType, List<String>>> = listOf(
        ActType.DOWRY_PROHIBITION to listOf(
            "dowry", "दहेज प्रतिषेध", "dahej pratishedh", "dowry prohibition"
        ),
        ActType.POCSO to listOf(
            "pocso", "protection of children from sexual", "यौन अपराधों से बच्चों"
        ),
        ActType.MUSLIM_WOMEN_DIVORCE to listOf(
            "muslim women", "muslim mahila", "मुस्लिम महिला", "protection of rights on divorce"
        ),
        ActType.MUSLIM_WOMEN_MARRIAGE to listOf(
            "muslim women", "protection of rights on marriage", "triple talaq"
        ),
        ActType.SC_ST_ATROCITIES to listOf(
            "atrocities", "sc/st", "scheduled castes", "scheduled tribes",
            "अनुसूचित जाति", "अनुसूचित जनजाति", "prevention of atrocities"
        ),
        ActType.IT_ACT to listOf(
            "information technology", "it act", "i.t. act", "सूचना प्रौद्योगिकी"
        ),
        ActType.ARMS_ACT to listOf(
            "arms act", "शस्त्र अधिनियम"
        ),
        ActType.NDPS to listOf(
            "ndps", "narcotic", "narcotic drugs", "psychotropic", "narcotics"
        ),
        ActType.UAPA to listOf(
            "uapa", "unlawful activities", "गैरकानूनी गतिविधियां"
        ),
        ActType.DOMESTIC_VIOLENCE to listOf(
            "domestic violence", "घरेलू हिंसा", "protection of women from domestic"
        ),
        ActType.IMMORAL_TRAFFIC to listOf(
            "immoral traffic", "itpa", "अनैतिक व्यापार"
        ),
        ActType.JUVENILE_JUSTICE to listOf(
            "juvenile justice", "किशोर न्याय", "jj act"
        )
    )

    fun detectActType(actName: String): ActType {
        val lower = actName.lowercase()
        // Special case: Muslim Women Act — distinguish by keyword
        if (lower.contains("muslim") || lower.contains("मुस्लिम")) {
            return if (lower.contains("marriage") || lower.contains("विवाह") || lower.contains("talaq"))
                ActType.MUSLIM_WOMEN_MARRIAGE
            else
                ActType.MUSLIM_WOMEN_DIVORCE
        }
        for ((type, patterns) in actNamePatterns) {
            if (patterns.any { lower.contains(it.lowercase()) }) return type
        }
        return ActType.UNKNOWN
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION DATABASE
    // ─────────────────────────────────────────────────────────────────────────

    val entries: List<OtherActEntry> = listOf(

        // ── Dowry Prohibition Act, 1961 ───────────────────────────────────────
        OtherActEntry(ActType.DOWRY_PROHIBITION, "2",
            "Definition of Dowry",
            "Defines 'dowry' as any property given at or before/after marriage in connection with the marriage.",
            BnsSeverityDb.SeverityLevel.LOW, false, true,
            "N/A (Definition Section)", 2),
        OtherActEntry(ActType.DOWRY_PROHIBITION, "3",
            "Giving/Taking Dowry",
            "Giving or taking dowry or abetting such giving or taking.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "5 years + ₹15,000 or value of dowry (whichever is higher)", 7),
        OtherActEntry(ActType.DOWRY_PROHIBITION, "4",
            "Demanding Dowry",
            "Demanding dowry directly or indirectly from parents/guardian of bride or bridegroom.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "6 months to 2 years + fine up to ₹10,000", 7),
        OtherActEntry(ActType.DOWRY_PROHIBITION, "4A",
            "Dowry Advertisement Ban",
            "Publishing advertisements offering property/money as consideration for marriage.",
            BnsSeverityDb.SeverityLevel.MEDIUM, true, true,
            "6 months to 5 years or fine up to ₹15,000", 5),
        OtherActEntry(ActType.DOWRY_PROHIBITION, "6",
            "Dowry to be for Benefit of Wife",
            "All dowry received to be held in trust for the wife.",
            BnsSeverityDb.SeverityLevel.MEDIUM, false, true,
            "6 months to 2 years or fine or both", 4),
        OtherActEntry(ActType.DOWRY_PROHIBITION, "8B",
            "Dowry Prohibition Officers",
            "Powers and functions of Dowry Prohibition Officers.",
            BnsSeverityDb.SeverityLevel.LOW, false, true,
            "N/A (Administrative)", 2),

        // ── POCSO Act, 2012 ──────────────────────────────────────────────────
        OtherActEntry(ActType.POCSO, "3",
            "Penetrative Sexual Assault",
            "Penetrative sexual assault on a child (below 18 years).",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Minimum 10 years to Life imprisonment + fine", 10),
        OtherActEntry(ActType.POCSO, "4",
            "Punishment for Penetrative Sexual Assault",
            "Punishment for committing penetrative sexual assault on a child.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Minimum 20 years to Life imprisonment or Death + fine", 10),
        OtherActEntry(ActType.POCSO, "5",
            "Aggravated Penetrative Sexual Assault",
            "Penetrative sexual assault by police/armed forces/gang/repeat offender on child.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Minimum 20 years to Life imprisonment or Death + fine", 10),
        OtherActEntry(ActType.POCSO, "6",
            "Punishment for Aggravated Penetrative Sexual Assault",
            "Enhanced punishment for aggravated penetrative sexual assault on a child.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Death or Life imprisonment + fine", 10),
        OtherActEntry(ActType.POCSO, "7",
            "Sexual Assault",
            "Sexual assault on a child not amounting to penetrative sexual assault.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "3 to 5 years + fine", 9),
        OtherActEntry(ActType.POCSO, "8",
            "Punishment for Sexual Assault",
            "Punishment for committing sexual assault on a child.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "3 to 5 years + fine", 9),
        OtherActEntry(ActType.POCSO, "9",
            "Aggravated Sexual Assault",
            "Sexual assault (non-penetrative) by authority figure, gang, or on a disabled child.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "5 to 7 years + fine", 9),
        OtherActEntry(ActType.POCSO, "10",
            "Punishment for Aggravated Sexual Assault",
            "Enhanced punishment for aggravated sexual assault on a child.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "5 to 7 years + fine", 9),
        OtherActEntry(ActType.POCSO, "11",
            "Sexual Harassment of Child",
            "Sexual harassment of a child including exhibitionism, showing pornography, or stalking.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "3 years + fine", 8),
        OtherActEntry(ActType.POCSO, "12",
            "Punishment for Sexual Harassment",
            "Punishment for sexually harassing a child.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "3 years + fine", 8),
        OtherActEntry(ActType.POCSO, "13",
            "Use of Child for Pornography",
            "Using a child for pornographic purposes or production of pornographic material.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "5 years + fine (first conviction); 7 years + fine (subsequent)", 10),
        OtherActEntry(ActType.POCSO, "14",
            "Punishment for Use of Child in Pornography",
            "Punishment for using a child for pornographic material.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "5 to 7 years + fine", 10),
        OtherActEntry(ActType.POCSO, "17",
            "Abetment of Offence under POCSO",
            "Abetting any offence under POCSO; if abetted offence is committed, same punishment.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Same as the offence abetted", 9),
        OtherActEntry(ActType.POCSO, "19",
            "Reporting of Offence",
            "Mandatory reporting of POCSO offences — failure to report is punishable.",
            BnsSeverityDb.SeverityLevel.MEDIUM, false, true,
            "6 months or fine or both", 4),

        // ── Muslim Women (Protection of Rights on Divorce) Act, 1986 ─────────
        OtherActEntry(ActType.MUSLIM_WOMEN_DIVORCE, "3",
            "Mahr and Maintenance on Divorce",
            "A divorced Muslim woman is entitled to a reasonable and fair provision and maintenance from her husband.",
            BnsSeverityDb.SeverityLevel.MEDIUM, false, true,
            "Court-ordered maintenance/provision", 4),
        OtherActEntry(ActType.MUSLIM_WOMEN_DIVORCE, "4",
            "Order for Payment of Maintenance",
            "Magistrate may order husband to pay maintenance if he fails to do so.",
            BnsSeverityDb.SeverityLevel.MEDIUM, false, true,
            "Imprisonment up to 1 year or fine or both (for non-compliance)", 5),

        // ── Muslim Women (Protection of Rights on Marriage) Act, 2019 ─────────
        OtherActEntry(ActType.MUSLIM_WOMEN_MARRIAGE, "3",
            "Triple Talaq Void",
            "Any pronouncement of talaq by a Muslim husband upon his wife (instant triple talaq) is void and illegal.",
            BnsSeverityDb.SeverityLevel.HIGH, true, true,
            "N/A (Declaratory — makes Triple Talaq void)", 6),
        OtherActEntry(ActType.MUSLIM_WOMEN_MARRIAGE, "4",
            "Punishment for Triple Talaq",
            "Muslim husband pronouncing instant triple talaq on his wife is punishable.",
            BnsSeverityDb.SeverityLevel.HIGH, true, true,
            "3 years imprisonment + fine", 7),
        OtherActEntry(ActType.MUSLIM_WOMEN_MARRIAGE, "5",
            "Allowance to Muslim Woman",
            "A Muslim woman upon whom talaq is pronounced is entitled to subsistence allowance from her husband.",
            BnsSeverityDb.SeverityLevel.MEDIUM, false, true,
            "Court-determined subsistence allowance", 4),
        OtherActEntry(ActType.MUSLIM_WOMEN_MARRIAGE, "7",
            "Cognisance of Offence",
            "Offence under this Act is cognisable, compoundable (with wife's consent), and bailable.",
            BnsSeverityDb.SeverityLevel.MEDIUM, true, true,
            "N/A (Procedural)", 3),

        // ── SC/ST (Prevention of Atrocities) Act, 1989 ───────────────────────
        OtherActEntry(ActType.SC_ST_ATROCITIES, "3(1)(r)",
            "Intentional Insult / Intimidation",
            "Intentionally insulting or intimidating a member of SC/ST in a public place.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "6 months to 5 years + fine", 7),
        OtherActEntry(ActType.SC_ST_ATROCITIES, "3(1)(s)",
            "Abusing in Public",
            "Abusing a member of SC/ST by caste name in public view.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "6 months to 5 years + fine", 7),
        OtherActEntry(ActType.SC_ST_ATROCITIES, "3(2)(v)",
            "False Evidence in Capital Cases",
            "Giving false evidence to secure conviction/death of SC/ST member for capital offence.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Death or Life imprisonment + fine", 10),
        OtherActEntry(ActType.SC_ST_ATROCITIES, "3(1)",
            "Atrocities against SC/ST",
            "Commission of atrocities (hurt, assault, sexual exploitation, land dispossession) against SC/ST.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "6 months to 5 years + fine (varies by sub-section)", 8),
        OtherActEntry(ActType.SC_ST_ATROCITIES, "3(2)",
            "Aggravated Atrocities",
            "Aggravated atrocities including false evidence, arson, damage to property of SC/ST.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "6 months to Life imprisonment (varies by sub-section)", 9),
        OtherActEntry(ActType.SC_ST_ATROCITIES, "4",
            "Wilful Neglect by Public Servant",
            "Public servant (non-SC/ST) wilfully neglecting duties under this Act.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "6 months to 1 year imprisonment", 7),

        // ── Information Technology Act, 2000 ──────────────────────────────────
        OtherActEntry(ActType.IT_ACT, "66",
            "Computer Related Offences",
            "Dishonestly/fraudulently doing any act referred to in Section 43 (damage to computer).",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "3 years imprisonment or ₹5 lakh fine or both", 7),
        OtherActEntry(ActType.IT_ACT, "66A",
            "Offensive Messages (Struck Down)",
            "Sending offensive messages through communication service (NOTE: struck down by SC in 2015).",
            BnsSeverityDb.SeverityLevel.LOW, false, true,
            "N/A — Section 66A struck down by Supreme Court (Shreya Singhal v. UOI, 2015)", 2),
        OtherActEntry(ActType.IT_ACT, "66C",
            "Identity Theft",
            "Fraudulently or dishonestly making use of the electronic signature, password or unique identification feature of any other person.",
            BnsSeverityDb.SeverityLevel.HIGH, true, true,
            "3 years imprisonment + ₹1 lakh fine", 5),
        OtherActEntry(ActType.IT_ACT, "66D",
            "Cheating by Personation using Computer",
            "Cheating by personating by using a computer resource.",
            BnsSeverityDb.SeverityLevel.HIGH, true, true,
            "3 years imprisonment + ₹1 lakh fine", 7),
        OtherActEntry(ActType.IT_ACT, "66E",
            "Violation of Privacy",
            "Intentionally capturing, publishing, or transmitting image of private area of any person without consent.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "3 years imprisonment or ₹2 lakh fine or both", 7),
        OtherActEntry(ActType.IT_ACT, "67",
            "Publishing Obscene Material Online",
            "Publishing or transmitting obscene material in electronic form.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "3 years + ₹5 lakh fine (first); 5 years + ₹10 lakh (subsequent)", 7),
        OtherActEntry(ActType.IT_ACT, "67A",
            "Publishing Sexually Explicit Material Online",
            "Publishing or transmitting material containing sexually explicit act in electronic form.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "5 years + ₹10 lakh fine (first); 7 years + ₹10 lakh (subsequent)", 9),
        OtherActEntry(ActType.IT_ACT, "67B",
            "Child Pornography Online",
            "Publishing or transmitting material depicting children in sexually explicit act online.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "5 years + ₹10 lakh fine (first); 7 years + ₹10 lakh (subsequent)", 10),
        OtherActEntry(ActType.IT_ACT, "72",
            "Breach of Confidentiality and Privacy",
            "Disclosure of information accessed during lawful services, in breach of lawful contract.",
            BnsSeverityDb.SeverityLevel.MEDIUM, false, true,
            "2 years or ₹1 lakh fine or both", 5),

        // ── Arms Act, 1959 ────────────────────────────────────────────────────
        OtherActEntry(ActType.ARMS_ACT, "25",
            "Punishment for Contravention of Section 3",
            "Acquiring, having in possession or carrying any firearm or ammunition without licence.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "3 years to 7 years + fine", 8),
        OtherActEntry(ActType.ARMS_ACT, "27",
            "Punishment for Use of Arms",
            "Using arms in contravention of Section 5 (prohibited arms) — includes AK-47 etc.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Minimum 7 years to Life imprisonment + fine", 9),
        OtherActEntry(ActType.ARMS_ACT, "3",
            "Licence for Firearms",
            "Requirement of licence for acquisition, possession, or carrying of firearms.",
            BnsSeverityDb.SeverityLevel.MEDIUM, true, false,
            "Offence under Section 25 applies", 6),
        OtherActEntry(ActType.ARMS_ACT, "5",
            "Prohibited Arms",
            "Prohibition on acquisition, possession, manufacture, sale of prohibited arms.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Life imprisonment + fine", 10),

        // ── NDPS Act, 1985 ────────────────────────────────────────────────────
        OtherActEntry(ActType.NDPS, "20",
            "Punishment for Offences re: Cannabis",
            "Producing, manufacturing, possessing, selling, purchasing, transporting cannabis.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "6 months to 20 years + fine (depends on quantity)", 8),
        OtherActEntry(ActType.NDPS, "21",
            "Punishment re: Manufactured Drugs",
            "Offences involving manufactured drugs and their preparations.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "10 years to 20 years + fine (commercial quantity)", 9),
        OtherActEntry(ActType.NDPS, "22",
            "Punishment re: Psychotropic Substances",
            "Offences involving psychotropic substances.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "10 years to 20 years + fine (commercial quantity)", 9),
        OtherActEntry(ActType.NDPS, "27",
            "Punishment for Consumption",
            "Consuming any narcotic drug or psychotropic substance.",
            BnsSeverityDb.SeverityLevel.MEDIUM, true, false,
            "6 months to 1 year or fine up to ₹20,000", 5),
        OtherActEntry(ActType.NDPS, "29",
            "Abetment and Criminal Conspiracy",
            "Abetment of or criminal conspiracy to commit any offence under NDPS.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Same as the principal offence", 9),

        // ── UAPA, 1967 ────────────────────────────────────────────────────────
        OtherActEntry(ActType.UAPA, "10",
            "Membership of Unlawful Association",
            "Being a member of an unlawful association after it is declared unlawful.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "2 years imprisonment + fine", 8),
        OtherActEntry(ActType.UAPA, "13",
            "Punishment for Unlawful Activities",
            "Doing any unlawful activity or abetting such activity.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "7 years imprisonment + fine", 9),
        OtherActEntry(ActType.UAPA, "15",
            "Terrorist Acts",
            "Committing, abetting, or conspiring to commit a terrorist act.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Death or Life imprisonment + fine", 10),
        OtherActEntry(ActType.UAPA, "16",
            "Punishment for Terrorist Acts",
            "Punishment for committing a terrorist act resulting in death or grievous hurt.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Death or Life imprisonment + fine", 10),
        OtherActEntry(ActType.UAPA, "17",
            "Raising Funds for Terrorist Acts",
            "Raising funds for a terrorist act.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "Life imprisonment + fine", 10),

        // ── Protection of Women from Domestic Violence Act, 2005 ─────────────
        OtherActEntry(ActType.DOMESTIC_VIOLENCE, "3",
            "Definition of Domestic Violence",
            "Includes physical, sexual, verbal, emotional, and economic abuse against women.",
            BnsSeverityDb.SeverityLevel.HIGH, false, true,
            "N/A (Definition — remedies through civil orders)", 6),
        OtherActEntry(ActType.DOMESTIC_VIOLENCE, "12",
            "Application to Magistrate",
            "Aggrieved person or Protection Officer can apply for orders to Magistrate.",
            BnsSeverityDb.SeverityLevel.MEDIUM, false, true,
            "N/A (Civil remedy — protection/residence/monetary orders)", 4),
        OtherActEntry(ActType.DOMESTIC_VIOLENCE, "17",
            "Right to Reside in Shared Household",
            "Every woman in domestic relationship has right to reside in shared household.",
            BnsSeverityDb.SeverityLevel.MEDIUM, false, true,
            "N/A (Civil remedy)", 3),
        OtherActEntry(ActType.DOMESTIC_VIOLENCE, "31",
            "Breach of Protection Order",
            "Breach of any protection order or interim protection order by respondent.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "1 year or ₹20,000 fine or both (subsequent: 2 years)", 7),

        // ── Immoral Traffic (Prevention) Act, 1956 ───────────────────────────
        OtherActEntry(ActType.IMMORAL_TRAFFIC, "3",
            "Punishment for Keeping a Brothel",
            "Keeping or managing or acting or assisting in the keeping of a brothel.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "1 to 3 years + fine (first conviction); 2 to 5 years (subsequent)", 9),
        OtherActEntry(ActType.IMMORAL_TRAFFIC, "4",
            "Living on Earnings of Prostitution",
            "Knowingly living on the earnings of the prostitution of another person.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "2 years + fine", 8),
        OtherActEntry(ActType.IMMORAL_TRAFFIC, "5",
            "Procuring/Inducing for Prostitution",
            "Procuring or inducing a person for the sake of prostitution.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "3 to 7 years + fine", 10),

        // ── Juvenile Justice (Care and Protection of Children) Act, 2015 ─────
        OtherActEntry(ActType.JUVENILE_JUSTICE, "75",
            "Cruelty to Child",
            "Assault, abandonment, exposure, or wilful neglect causing unnecessary mental or physical suffering to a child.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "3 years or ₹1 lakh fine or both", 8),
        OtherActEntry(ActType.JUVENILE_JUSTICE, "76",
            "Employment of Child for Begging",
            "Employing or using a child for the purpose of begging.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "5 years + fine (if child is maimed: 7 years)", 8),
        OtherActEntry(ActType.JUVENILE_JUSTICE, "77",
            "Giving Intoxicating Liquor to Child",
            "Giving an intoxicating liquor, narcotic drug, or tobacco product to a child.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "7 years + fine", 8),
        OtherActEntry(ActType.JUVENILE_JUSTICE, "78",
            "Using Child for Vending, etc.",
            "Using a child for illegal trafficking, vending, or similar purposes.",
            BnsSeverityDb.SeverityLevel.CRITICAL, true, false,
            "7 years + fine", 9),
        OtherActEntry(ActType.JUVENILE_JUSTICE, "79",
            "Exploitation of Child Employee",
            "Exploitation of a child employee, including withholding wages.",
            BnsSeverityDb.SeverityLevel.HIGH, true, false,
            "5 years + fine", 7)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Lookup helpers
    // ─────────────────────────────────────────────────────────────────────────

    private val byActAndSection: Map<Pair<ActType, String>, OtherActEntry> =
        entries.associateBy { Pair(it.actType, it.section) }

    fun find(actType: ActType, section: String): OtherActEntry? {
        val clean = section.trim()
        return byActAndSection[Pair(actType, clean)]
            ?: byActAndSection[Pair(actType, clean.substringBefore("("))]
    }

    fun computeSeverity(actType: ActType, sections: List<String>): OtherActSeverityResult {
        if (sections.isEmpty()) return OtherActSeverityResult(
            BnsSeverityDb.SeverityLevel.LOW, false, true, 1, emptyList(), emptyList()
        )

        val matched   = mutableListOf<OtherActEntry>()
        val unmatched = mutableListOf<String>()

        sections.forEach { sec ->
            find(actType, sec)?.let { matched.add(it) } ?: unmatched.add(sec)
        }

        if (matched.isEmpty()) return OtherActSeverityResult(
            BnsSeverityDb.SeverityLevel.MEDIUM, true, true, 5, emptyList(), unmatched
        )

        val sorted = matched.sortedByDescending { it.urgencyPoints }
        var score  = sorted.first().urgencyPoints.toFloat()
        score += sorted.drop(1).count { it.urgencyPoints >= 7 } * 0.5f
        val finalScore = score.coerceIn(1f, 10f).toInt()

        val level = when (finalScore) {
            in 1..3 -> BnsSeverityDb.SeverityLevel.LOW
            in 4..5 -> BnsSeverityDb.SeverityLevel.MEDIUM
            in 6..7 -> BnsSeverityDb.SeverityLevel.HIGH
            else    -> BnsSeverityDb.SeverityLevel.CRITICAL
        }

        return OtherActSeverityResult(
            level      = level,
            cognisable = matched.any { it.cognisable },
            bailable   = matched.all { it.bailable },
            urgencyScore = finalScore,
            matchedEntries = matched,
            unmatchedSecs  = unmatched
        )
    }

    /**
     * Returns true if the given act name belongs to a non-BNS act
     * that should NOT go through IPC→BNS remapping.
     */
    fun isNonBnsAct(actName: String): Boolean =
        detectActType(actName) != ActType.UNKNOWN
}