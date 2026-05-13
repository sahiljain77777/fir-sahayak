package com.example.firsahayak

/**
 * BNS (Bharatiya Nyaya Sanhita, 2023) Section Severity Database.
 *
 * Each entry contains:
 *  - section       : BNS section string (may include sub-section, e.g. "103(1)")
 *  - shortTitle    : One-line English title shown in UI
 *  - definition    : Plain-language one-line definition shown in UI
 *  - severity      : LOCAL severity level (independent of Gemma)
 *  - cognisable    : Whether the offence is cognisable (police can arrest without warrant)
 *  - bailable      : Whether the offence is bailable
 *  - maxPunishment : Maximum punishment string for UI display
 *  - urgencyPoints : Raw points used for urgency score calculation (0-10 scale)
 *
 * SEVERITY CALCULATION LOGIC (see computeSeverity()):
 *   - Pulls the highest urgencyPoints among all matched sections
 *   - Adds +1 for each additional high/critical section (conspiracy bump)
 *   - Clamps result to 1-10
 *   - Derives level: 1-3=LOW, 4-5=MEDIUM, 6-7=HIGH, 8-10=CRITICAL
 *   - Cognisable = true if ANY section is cognisable
 *   - Bailable   = false if ANY section is non-bailable
 */
object BnsSeverityDb {

    enum class SeverityLevel { LOW, MEDIUM, HIGH, CRITICAL }

    data class BnsEntry(
        val section       : String,
        val shortTitle    : String,
        val definition    : String,
        val severity      : SeverityLevel,
        val cognisable    : Boolean,
        val bailable      : Boolean,
        val maxPunishment : String,
        val urgencyPoints : Int
    )

    data class SeverityResult(
        val level         : SeverityLevel,
        val cognisable    : Boolean,
        val bailable      : Boolean,
        val urgencyScore  : Int,
        val matchedEntries: List<BnsEntry>,
        val unmatchedSecs : List<String>
    )

    val entries: List<BnsEntry> = listOf(



        BnsEntry("3(5)", "Common Intention (Joint Liability)", "When a criminal act is done by several persons in furtherance of the common intention of all, each is liable as if he did it alone.", SeverityLevel.MEDIUM, true, true, "Same as the substantive offence committed.", 5),


        // ── Chapter IV: ABETMENT, CRIMINAL CONSPIRACY AND ATTEMPT ───────────────

        BnsEntry("45", "Abetment", "Instigating or conspiracy to commit an offence.", SeverityLevel.MEDIUM, true, true, "Same as offence abetted", 5),
        BnsEntry("46",  "Abettor defined","Person who abets commission of an offence or conspiracy.",SeverityLevel.MEDIUM, true, true, "Same as offence abetted", 5),
        BnsEntry("47",  "Abetment — act committed outside India", "Punishment when the abetted act is actually committed outside India.", SeverityLevel.MEDIUM, true, true, "Same as offence abetted", 5),
        BnsEntry("48",  "Abetment — act committed within India", "Punishment when the abetted act is actually committed within India.", SeverityLevel.MEDIUM, true, true, "Same as offence abetted", 5),
        BnsEntry("49", "Punishment of Abetment", "Punishment for abetment if the act abetted is committed in consequence and no express provision is made for its punishment.", SeverityLevel.HIGH, true, false, "Same punishment as provided for the offence abetted.", 7),
        BnsEntry("55", "Abetment of Offence (Death/Life)", "Abetting an offence punishable with death or imprisonment for life, if the offence be not committed.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine.", 8),
        BnsEntry("61", "Criminal Conspiracy", "Agreement between two or more persons to commit an illegal act.", SeverityLevel.MEDIUM, true, true, "6 months or fine", 5),
        BnsEntry("61(1)", "Criminal Conspiracy", "Agreement between two or more persons to commit an illegal act.", SeverityLevel.MEDIUM, true, true, "6 months or fine", 5),
        BnsEntry("61(2)", "Punishment for Conspiracy", "Conspiracy to commit serious offences.", SeverityLevel.HIGH, true, false, "Same as abetment", 7),

        // ── Chapter V: Offences Against Women and Children ──────────────────
        BnsEntry("63", "Rape", "Sexual intercourse without consent.", SeverityLevel.CRITICAL, true, false, "10 years to Life", 10),
        BnsEntry("64", "Punishment for Rape", "Rigorous imprisonment for rape.", SeverityLevel.CRITICAL, true, false, "10 years to Life", 10),

        BnsEntry("65(1)", "Rape (Woman under 16)", "Committing rape on a woman under sixteen years of age.", SeverityLevel.CRITICAL, true, false, "20 years to Life (remainder of natural life) + fine", 10),
        BnsEntry("65(2)", "Rape (Woman under 12)", "Committing rape on a woman under twelve years of age.", SeverityLevel.CRITICAL, true, false, "20 years to Life (remainder of natural life) or death + fine", 10),
        BnsEntry("66", "Rape (Death/Vegetative)", "Rape resulting in death or persistent vegetative state.", SeverityLevel.CRITICAL, true, false, "20 years to Life/Death", 10),
        BnsEntry("67", "Intercourse during Separation", "Sexual intercourse by a man with his own wife during separation.", SeverityLevel.HIGH, true, true, "2 to 7 years + fine", 7),
        BnsEntry("68", "Intercourse by Authority", "Sexual intercourse by a person in authority (jailer, hospital staff, etc.).", SeverityLevel.HIGH, true, false, "5 to 10 years + fine", 8),
        BnsEntry("69", "Sexual Intercourse by Deceit", "Intercourse by false promise of marriage or suppressing identity.", SeverityLevel.HIGH, true, false, "10 years + fine", 8),
        BnsEntry("70(1)",  "Gang Rape", "Rape committed by a group of persons acting with common intention.", SeverityLevel.CRITICAL, true, false, "minimum 20 years of rigorous imprisonment which may extend to life imprisonment", 10),
        BnsEntry("70(2)", "Gang Rape on women under 18", "Rape committed by a group of persons acting with common intention on woman under the age of eighteen.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or Death Penalty", 10),
        BnsEntry("71", "Repeat Offenders", "Enhanced punishment for repeat offenders of certain sexual crimes.", SeverityLevel.CRITICAL, true, false, "Life or Death", 10),
        BnsEntry("72", "Identity Disclosure", "Disclosure of the identity of a victim of certain sexual offences.", SeverityLevel.MEDIUM, true, true, "2 years + fine", 5),
        BnsEntry("73", "Publishing Court Proceedings", "Printing or publishing any matter relating to court proceedings without permission.", SeverityLevel.MEDIUM, false, true, "2 years + fine", 4),
        BnsEntry("74", "Outraging Modesty", "Assault or criminal force to outrage woman's modesty.", SeverityLevel.HIGH, true, false, "1 to 5 years", 7),
        BnsEntry("75",  "Sexual Harassment", "Making sexually coloured remarks, physical contact, or demand for sexual favours.", SeverityLevel.HIGH, true, false, "3 years imprisonment / fine", 7),
        BnsEntry("76",  "Assault to disrobe", "Assault with intent to disrobe or compel a woman to be naked.", SeverityLevel.HIGH, true, false, "3 to 7 years imprisonment", 8),
        BnsEntry("77",  "Voyeurism", "Watching or capturing image of a woman in private act without consent.", SeverityLevel.HIGH, true, false, "1 to 7 years imprisonment", 7),
        BnsEntry("78", "Stalking", "Repeatedly following or contacting a woman despite disinterest.", SeverityLevel.MEDIUM, true, false, "3 years (1st conviction)", 6),
        BnsEntry("79", "Insulting Modesty", "Word, gesture or act intended to insult woman's modesty.", SeverityLevel.MEDIUM, true, true, "3 years + fine", 5),
        BnsEntry("80", "Dowry Death", "Death within 7 years of marriage involving dowry harassment.", SeverityLevel.CRITICAL, true, false, "7 years to Life", 9),
        BnsEntry("81", "Deceitful Cohabitation", "Man deceitfully inducing a belief of lawful marriage to cause cohabitation.", SeverityLevel.HIGH, true, false, "10 years + fine.", 8),
        BnsEntry("82(1)", "Marrying Again During Lifetime of Spouse", "Marrying while having a spouse living, where such marriage is void by reason of its taking place during the life of such spouse.", SeverityLevel.MEDIUM, false, true, "7 years + fine", 6),
        BnsEntry("82(2)", "Bigamy with Concealment", "Committing bigamy having concealed the fact of the former marriage from the person with whom the subsequent marriage is contracted.", SeverityLevel.HIGH, false, true, "10 years + fine", 7),
        BnsEntry("83", "Fraudulent Marriage Ceremony", "Marriage ceremony fraudulently gone through without lawful marriage.", SeverityLevel.MEDIUM, true, false, "7 years + fine.", 6),
        BnsEntry("85", "Cruelty by Husband/Relatives", "Subjecting a woman to physical or mental cruelty.", SeverityLevel.HIGH, true, false, "3 years + fine", 7),
        BnsEntry("86",  "Cruelty — definition", "Defines conduct causing grave injury or forcing unlawful demand on a woman.", SeverityLevel.HIGH, true, false, "3 years imprisonment + fine", 7),
        BnsEntry("87", "Abducting Woman to Compel Marriage", "Kidnapping or abducting a woman to force marriage or illicit intercourse against her will.", SeverityLevel.CRITICAL, true, false, "10 years + fine", 9),


        BnsEntry("88", "Causing Miscarriage", "Voluntarily causing miscarriage (if not in good faith to save life).", SeverityLevel.HIGH, true, false, "3 to 7 years + fine", 8),
        BnsEntry("89", "Miscarriage without Consent", "Causing miscarriage without the woman's consent.", SeverityLevel.CRITICAL, true, false, "Life or 10 years + fine", 10),
        BnsEntry("90", "Death by Miscarriage Attempt", "Death caused by an act intended to cause miscarriage.", SeverityLevel.CRITICAL, true, false, "10 years (or Life if no consent)", 10),
        BnsEntry("91", "Preventing Child Born Alive", "Act done with intent to prevent child being born alive or cause death after birth.", SeverityLevel.CRITICAL, true, false, "10 years or fine", 9),

        BnsEntry("92", "Death of Quick Unborn Child", "Causing death of a quick unborn child by act amounting to culpable homicide.", SeverityLevel.CRITICAL, true, false, "10 years + fine", 9),
        BnsEntry("93", "Abandonment of Child", "Exposure or abandonment of child under twelve years by parent or guardian.", SeverityLevel.HIGH, true, false, "7 years or fine", 8),
        BnsEntry("94", "Concealment of Birth", "Secret disposal of dead body to conceal birth of child.", SeverityLevel.MEDIUM, true, true, "2 years or fine", 4),
        BnsEntry("95", "Engaging Child for Offence", "Hiring or employing a child to commit a crime.", SeverityLevel.HIGH, true, false, "3 to 10 years", 8),
        BnsEntry("96", "Procuration of Child", "Inducing a child under eighteen years to go from any place or to do any act with intent that such child may be or knowing that it is likely that such child will be forced or seduced to illicit intercourse.", SeverityLevel.CRITICAL, true, false, "10 years + fine.", 10),
        BnsEntry("97", "Kidnapping Child under 10", "Kidnapping or abducting a child under 10 from lawful guardianship.", SeverityLevel.HIGH, true, false, "7 years + fine", 8),
        BnsEntry("98", "Selling Child for Prostitution", "Selling, letting to hire, or otherwise disposing of a child under eighteen years for purposes of prostitution or illicit intercourse.", SeverityLevel.CRITICAL, true, false, "7 to 14 years + fine.", 10),
        BnsEntry("99", "Buying Child for Prostitution", "Buying or obtaining a child under 18 for purposes of prostitution or illegal intercourse.", SeverityLevel.CRITICAL, true, false, "10 years + fine", 10),


        // ── Chapter VI: Offences affecting Human Body ─────────────────────────

        BnsEntry("100", "Culpable Homicide", "Causing death with intention or knowledge that death is likely.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 9),
        BnsEntry("101", "Murder", "Intentional killing.", SeverityLevel.CRITICAL, true, false, "Death or Life", 10),
        BnsEntry("103(1)", "Punishment for Murder", "Standard punishment for murder.", SeverityLevel.CRITICAL, true, false, "Death or Life", 10),
        BnsEntry("103(2)", "Group Murder based on Caste, Race or Community", "Murder by group of 5+ based on race, caste, community, etc.", SeverityLevel.CRITICAL, true, false, "Death or Life", 10),
        BnsEntry("104", "Murder by Life-convict", "Whoever, being under sentence of imprisonment for life, commits murder.", SeverityLevel.CRITICAL, true, false, "Death or imprisonment for life (remainder of natural life) + fine", 10),
        BnsEntry("105", "Culpable Homicide not amounting to Murder", "Causing death without the full intention required for murder.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 9),
        BnsEntry("106(1)", "Death by Negligence", "Causing death by rash or negligent act.", SeverityLevel.HIGH, true, true, "5 years + fine", 7),
        BnsEntry("106(2)", "Hit and Run", "Causing death by rash driving and escaping without reporting.", SeverityLevel.CRITICAL, true, false, "10 years + fine", 9),
        BnsEntry("107", "Abetment of suicide — minor/insane", "Abetting suicide of a child, insane person, or intoxicated person.", SeverityLevel.CRITICAL, true, false, "Death or life imprisonment", 9),
        BnsEntry("108", "Abetment of Suicide", "Instigating a person to commit suicide.", SeverityLevel.HIGH, true, false, "10 years + fine", 8),
        BnsEntry("109", "Attempt to Murder", "Doing any act with intention of committing murder.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 9),
        BnsEntry("111", "Organized Crime", "Crime by a syndicate for direct/indirect benefit.", SeverityLevel.CRITICAL, true, false, "5 years to Death", 10),
        BnsEntry("113", "Terrorist Act", "Acts threatening sovereignty or integrity of India.", SeverityLevel.CRITICAL, true, false, "5 years to Death", 10),
        BnsEntry("114", "Hurt", "Causing bodily pain, disease, or infirmity to any person.", SeverityLevel.MEDIUM, true, true, "1 year imprisonment / fine / both", 4),
        BnsEntry("115(1)", "Voluntarily Causing Hurt", "Intentionally causing harm to any person or with knowledge that he is likely thereby to cause harm to any person.", SeverityLevel.MEDIUM, true, true, "1 year / ₹10,000 fine / both", 5),
        BnsEntry("115(2)", "Voluntarily Causing Hurt", "Bodily pain/disease/infirmity (formerly IPC 323).", SeverityLevel.MEDIUM, true, true, "1 year or ₹10,000 fine", 4),
        BnsEntry("116", "Grievous Hurt", "Emasculation, permanent injury to sight/hearing, fractures, burns.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 7),
        BnsEntry("117(1)", "Voluntarily Causing Grievous Hurt (Definition)", "Causing grievous hurt with the intent or knowledge that such hurt is likely to be caused.", SeverityLevel.HIGH, true, false, "N/A (Definition Section)", 7),
        BnsEntry("117(2)", "Punishment for Grievous Hurt", "Standard punishment for causing grievous hurt (formerly IPC 325).", SeverityLevel.HIGH, true, false, "7 years + fine", 7),
        BnsEntry("117(3)", "Grievous Hurt (Permanent Disability/PVS)", "Causing hurt resulting in permanent disability or persistent vegetative state.", SeverityLevel.CRITICAL, true, false, "10 years to Life (Natural Life) + fine", 10),
        BnsEntry("117(4)", "Grievous Hurt by Group (Hate Crime)", "Five or more persons acting in concert causing grievous hurt based on race, caste, community, sex, etc.", SeverityLevel.CRITICAL, true, false, "7 years + fine", 9),
        BnsEntry("118(1)", "Voluntarily Causing Hurt by Dangerous Weapons", " Voluntarily causing hurt using dangerous weapons, fire, poison, etc..", SeverityLevel.HIGH, true, false, "3 years to life imprisonment or ₹20,000 or both ", 7),
        BnsEntry("118(2)", "Voluntarily Causing Grievous Hurt by Dangerous Weapons", "Voluntarily causing grievous hurt (e.g., permanent disfigurement, loss of body part) using dangerous weapons.",SeverityLevel.HIGH, true, false, "1-10 years +fine or life imprisonment.", 7),
        BnsEntry("119(1)", "Hurt to Extort Property", "Causing hurt to extort property or force signing of valuable security.", SeverityLevel.HIGH, true, false, "10 years imprisonment + fine", 8),
        BnsEntry("120(1)", "Hurt to Extort Confession or Property", "Voluntarily causing hurt to extort confession, information, or to compel restoration of property.", SeverityLevel.HIGH, true, false, "7 years + fine", 8),
        BnsEntry("120(2)", "Grievous Hurt to Extort Confession or Property", "Voluntarily causing grievous hurt to extort confession, information, or to compel restoration of property.", SeverityLevel.CRITICAL, true, false, "10 years + fine", 9),
        BnsEntry("121(1)", "Voluntarily Causing Hurt to Deter PS", "Voluntarily causing hurt to a public servant in the discharge of duty or to deter them from discharging duty.", SeverityLevel.HIGH, true, false, "5 years or fine or both", 8),
        BnsEntry("121(2)", "Voluntarily Causing Grievous Hurt to Deter PS", "Voluntarily causing grievous hurt to a public servant in the discharge of duty or to deter them from discharging duty.", SeverityLevel.CRITICAL, true, false, "1 year to 10 years + fine", 9),
        BnsEntry("122(1)", "Hurt on Provocation", "Voluntarily causing hurt on grave and sudden provocation to the person who gave the provocation.", SeverityLevel.LOW, true, true, "1 month or ₹5,000 fine or both", 3),
        BnsEntry("122(2)", "Grievous Hurt on Provocation", "Voluntarily causing grievous hurt on grave and sudden provocation to the person who gave the provocation.", SeverityLevel.MEDIUM, true, true, "5 years or ₹10,000 fine or both", 6),
        BnsEntry("123", "Hurt by Poison to Commit Offence", "Administering poison or any stupefying/intoxicating substance with intent to commit an offence.", SeverityLevel.HIGH, true, false, "10 years + fine", 8),
        BnsEntry("124(1)", "Acid Attack", "Grievous hurt by use of acid.", SeverityLevel.CRITICAL, true, false, "10 years to Life imprisonment", 10),
        BnsEntry("124(2)", "Attempt to Acid Attack", "Attempt to acid attack with the intention of causing grievous hurt", SeverityLevel.CRITICAL, true, false, "5-7 years to Life imprisonment", 10),
        BnsEntry("125(a)", "Act Endangering Life","Doing any rash or negligent act that endangers human life or safety.", SeverityLevel.MEDIUM, true, true, "3 months / ₹2,500 fine / both", 4),
        BnsEntry("125(b)", "Causing Hurt by Endangering Act", "Causing hurt by rash or negligent act endangering life.", SeverityLevel.MEDIUM, true, true, "6 months / ₹5,000 fine / both", 5),
        BnsEntry("125(c)", "Causing Grievous Hurt by Endangering Act", "Causing grievous hurt by rash or negligent act.", SeverityLevel.HIGH, true, true, "2 years / fine / both", 6),
        BnsEntry("126(1)", "Wrongful Restraint", "Obstructing a person from proceeding in any direction they have a right to go.", SeverityLevel.LOW, true, true, "1 month / ₹5,000 fine / both", 3),
        BnsEntry("126(2)", "Wrongful Restraint", "Obstructing a person from proceeding in any direction.", SeverityLevel.LOW, true, true, "1 month or ₹5,000 fine", 3),
        BnsEntry("127(1)", "Wrongful Confinement (Definition)", "Restraining a person to prevent them from proceeding beyond certain circumscribing limits.", SeverityLevel.MEDIUM, true, true, "N/A (Definition Section)", 4),
        BnsEntry("127(2)", "Punishment for Wrongful Confinement", "Standard punishment for wrongful confinement.", SeverityLevel.MEDIUM, true, true, "1 year or ₹5,000 fine or both", 5),
        BnsEntry("127(3)", "Confinement for 3+ Days", "Wrongfully confining a person for three days or more.", SeverityLevel.HIGH, true, true, "3 years or ₹10,000 fine or both", 7),
        BnsEntry("127(4)", "Confinement for 10+ Days", "Wrongfully confining a person for ten days or more.", SeverityLevel.HIGH, true, true, "5 years + minimum ₹10,000 fine", 8),
        BnsEntry("127(5)", "Confinement despite Writ for Liberation", "Keeping a person confined knowing a writ for liberation has been issued.", SeverityLevel.HIGH, true, true, "2 years (additional) + fine", 7),
        BnsEntry("127(6)", "Secret Wrongful Confinement", "Confinement intended to be unknown to interested persons or public servants.", SeverityLevel.HIGH, true, true, "3 years (additional) + fine", 7),
        BnsEntry("127(7)", "Confinement to Extort Property", "Confinement for the purpose of extorting property/valuable security or constraining illegal acts.", SeverityLevel.HIGH, true, false, "3 years + fine", 8),
        BnsEntry("127(8)", "Confinement to Extort Confession", "Confinement to extort confession/information for detection of offence or restoration of property.", SeverityLevel.HIGH, true, false, "3 years + fine", 8),
        BnsEntry("130", "Assault", "Gesture causing apprehension of criminal force.", SeverityLevel.MEDIUM, true, true, "3 months + fine", 4),
        BnsEntry("131", "Criminal Force", "Using force intentionally on a person without their consent.", SeverityLevel.MEDIUM, true, true, "3 months / ₹1,000 / both", 4),
        BnsEntry("132", "Assault on Public Servant", "Force used to deter public servant from duty.", SeverityLevel.HIGH, true, false, "2 to 5 years", 7),
        BnsEntry("134", "Assault in Attempt to Commit Theft", "Assault or criminal force in attempt to commit theft of property carried by a person.", SeverityLevel.HIGH, true, false, "2 years or fine or both.", 7),
        BnsEntry("137(2)", "Punishment for Kidnapping", "Imprisonment for kidnapping from India or from lawful guardianship.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 8),
        BnsEntry("138", "Abduction", "Compelling or inducing a person to go from one place to another.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 7),
        BnsEntry("139", "Kidnapping/Maiming Child for Begging", "Kidnapping or maiming a child to use them for the purposes of begging.", SeverityLevel.CRITICAL, true, false, "Life imprisonment (natural life) + fine.", 10),
        BnsEntry("140(1)", "Kidnapping/Abducting to Murder", "Kidnapping or abducting any person in order that such person may be murdered.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 10),
        BnsEntry("140(2)", "Kidnapping for Ransom", "Kidnapping or abducting for ransom, threatening death or hurt.", SeverityLevel.CRITICAL, true, false, "Death or Life imprisonment + fine", 10),
        BnsEntry("140(3)", "Kidnapping for Secret Confinement", "Kidnapping or abducting with intent to cause person to be secretly and wrongfully confined.", SeverityLevel.HIGH, true, false, "7 years + fine", 8),
        BnsEntry("140(4)", "Kidnapping for Grievous Hurt/Slavery", "Kidnapping or abducting to subject person to grievous hurt, slavery, etc.", SeverityLevel.CRITICAL, true, false, "10 years + fine", 9),
        BnsEntry("141", "Importation of Girl or Boy from Foreign Country", "Importing a girl or boy under 21 years from a foreign country with specific intent.", SeverityLevel.HIGH, true, false, "10 years imprisonment + fine.", 8),
        BnsEntry("142", "Wrongfully Concealing Kidnapped Person", "Knowing a person has been kidnapped/abducted and wrongfully concealing or confining them.", SeverityLevel.HIGH, true, false, "Same as for kidnapping/abduction.", 8),
        BnsEntry("143", "Trafficking of Persons", "Trafficking of a person for exploitation including sexual exploitation or forced labour.", SeverityLevel.CRITICAL, true, false, "7 years to life imprisonment + fine", 9),
        BnsEntry("143(3)", "Trafficking of Persons", "Trafficking of a person for exploitation including sexual exploitation or forced labour.", SeverityLevel.CRITICAL, true, false, "7 years to life imprisonment + fine", 9),
        BnsEntry("144", "Sexual Exploitation of a Trafficked Child", "Exploitation of a trafficked child or person for sexual purposes.", SeverityLevel.CRITICAL, true, false, "5 to 10 years or Life imprisonment.", 10),
        BnsEntry("145", "Habitual Dealing in Slaves", "Habitually importing, exporting, removing, buying, or selling any person as a slave.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine.", 9),
        BnsEntry("146", "Unlawful Compulsory Labour", "Whoever unlawfully compels any person to labour against the will of that person.", SeverityLevel.MEDIUM, true, true, "1 year or fine or both.", 5),


        // ── Chapter VII: State & Sovereignty ─────────────────────────────────

        BnsEntry("147", "Waging War against Govt of India", "Waging, attempting to wage, or abetting the waging of war against the Government of India.", SeverityLevel.CRITICAL, true, false, "Death or Life imprisonment + fine.", 10),
        BnsEntry("148", "Conspiracy to Wage War", "Conspiring to commit offences punishable by Section 147 (waging war).", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine.", 10),
        BnsEntry("149", "Collecting Arms to Wage War", "Collecting arms, ammunition or otherwise preparing to wage war against the Govt.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine.", 10),
        BnsEntry("150", "Concealing Design to Wage War", "Concealing the existence of a design to wage war to facilitate such waging.", SeverityLevel.HIGH, true, false, "10 years + fine.", 8),
        BnsEntry("151", "Assaulting President/Governor", "Assaulting President or Governor with intent to compel or restrain any lawful power.", SeverityLevel.CRITICAL, true, false, "7 years + fine.", 9),
        BnsEntry("152", "Endangering Sovereignty", "Secession, armed rebellion, or separatist acts.", SeverityLevel.CRITICAL, true, false, "7 years to Life", 10),
        BnsEntry("156", "PS Allowing Prisoner of State to Escape", "Public servant voluntarily allowing a prisoner of State or war to escape from custody.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine.", 9),
        BnsEntry("158", "Aiding Escape of Prisoner of State", "Aiding escape of, rescuing, or harbouring a prisoner of State or war.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine.", 9),


        // ── Chapter VIII: Army, Navy, and Air Force ────────────────────────
        BnsEntry("159", "Abetting Mutiny", "Abetting mutiny or attempting to seduce a soldier, sailor or airman from duty.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 9),
        BnsEntry("160", "Abetment of Mutiny (Committed)", "Abetment of mutiny, if mutiny is committed in consequence thereof.", SeverityLevel.CRITICAL, true, false, "Death or Life imprisonment or 10 years + fine.", 10),
        BnsEntry("162", "Abetment of Assault by Soldier", "Abetment of assault by a soldier, sailor or airman on his superior officer, if the assault is committed.", SeverityLevel.HIGH, true, false, "7 years + fine.", 8),


        // ── Chapter IX: Offences Relating to Elections ─────────────────────
        BnsEntry("170", "Bribery at Elections", "Giving or accepting gratification for exercising electoral rights.", SeverityLevel.MEDIUM, false, true, "1 year or fine or both", 5),
        BnsEntry("171", "Undue Influence at Elections", "Interfering with the free exercise of any electoral right.", SeverityLevel.MEDIUM, false, true, "1 year or fine or both", 5),
        BnsEntry("172", "Personation at Elections", "Applying for a voting paper in the name of another person.", SeverityLevel.MEDIUM, false, true, "1 year or fine or both", 5),
        BnsEntry("173", "Punishment for Bribery", "Committing the offence of bribery; bribery by 'treating' (food, drink, entertainment) is punished with fine only.", SeverityLevel.MEDIUM, false, true, "1 year or fine or both; 'treating' punished with fine only.", 5),

        // ── Chapter X: Coin, Currency, and Stamps ──────────────────────────
        BnsEntry("178", "Counterfeiting Coin/Currency", "Counterfeiting coin, Government stamps, or currency notes.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 10),
        BnsEntry("179", "Using Counterfeit Coin/Stamp/Notes", "Using as genuine any forged or counterfeit coin, Government stamp, currency-notes or bank-notes.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine.", 10),
        BnsEntry("180", "Possession of Counterfeit Currency", "Possessing forged or counterfeit currency-notes or bank-notes.", SeverityLevel.HIGH, true, false, "7 years or fine or both", 8),
        BnsEntry("181", "Making Instruments for Forgery", "Making or possessing instruments or materials for forging or counterfeiting currency-notes.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine.", 10),
        BnsEntry("187", "Mint Employee Altering Coin", "Person employed in mint causing coin to be of different weight or composition from that fixed by law.", SeverityLevel.HIGH, true, false, "7 years + fine.", 8),
        BnsEntry("188", "Unlawfully Taking Instrument from Mint", "Unlawfully taking any coining tool or instrument from a mint.", SeverityLevel.HIGH, true, false, "7 years + fine.", 7),


        // ── Chapter XI: Offences relating to public tranquility ────────────────
        BnsEntry("189(1)", "Unlawful Assembly (Definition)", "Assembly of five or more persons with a common object to overawe government, resist law, or commit offences.", SeverityLevel.MEDIUM, true, true, "N/A (Definition Section)", 4),
        BnsEntry("189(2)", "Membership of Unlawful Assembly", "Intentionally joining or continuing in an assembly aware of facts that render it unlawful.", SeverityLevel.MEDIUM, true, true, "6 months or fine or both", 5),
        BnsEntry("189(3)", "Joining Assembly Commanded to Disperse", "Continuing in an unlawful assembly after it has been legally commanded to disperse.", SeverityLevel.HIGH, true, true, "2 years or fine or both", 7),
        BnsEntry("189(4)", "Unlawful Assembly Armed with Deadly Weapon", "Being a member of an unlawful assembly while armed with any deadly weapon.", SeverityLevel.HIGH, true, true, "2 years or fine or both", 8),
        BnsEntry("189(5)", "Joining Assembly Likely to Disturb Peace", "Joining or continuing in an assembly of 5+ persons likely to cause disturbance after command to disperse.", SeverityLevel.MEDIUM, true, true, "6 months or fine or both", 6),
        BnsEntry("189(6)", "Hiring Persons for Unlawful Assembly", "Hiring, engaging, or promoting the employment of persons to join an unlawful assembly.", SeverityLevel.MEDIUM, true, true, "Same as member + for any offence committed", 6),
        BnsEntry("189(7)", "Harbouring Persons Hired for Assembly", "Harbouring or receiving persons in premises knowing they are hired for an unlawful assembly.", SeverityLevel.MEDIUM, true, true, "6 months or fine or both", 5),
        BnsEntry("189(8)", "Being Hired for Unlawful Assembly", "Being engaged, hired, or offering to be hired to assist in acts of unlawful assembly.", SeverityLevel.MEDIUM, true, true, "6 months or fine or both", 5),
        BnsEntry("189(9)", "Being Hired and Armed with Deadly Weapon", "Being hired for an unlawful assembly and going armed with a deadly weapon.", SeverityLevel.HIGH, true, true, "2 years or fine or both", 7),
        BnsEntry("190", "Punishment for Unlawful Assembly", "Every member of an unlawful assembly guilty of the offence committed.", SeverityLevel.MEDIUM, true, true, "6 months / fine / both", 5),
        BnsEntry("191(1)", "Punishment for Rioting", "Rioting with use of force or violence by an unlawful assembly.", SeverityLevel.HIGH, true, false, "2 years imprisonment / fine / both", 7),
        BnsEntry("191(2)", "Punishment for Rioting", "Rioting with use of force or violence by an unlawful assembly.", SeverityLevel.HIGH, true, false, "2 years imprisonment / fine / both", 7),
        BnsEntry("191(3)", "Rioting with Deadly Weapon", "Rioting while armed with a deadly weapon.", SeverityLevel.HIGH, true, false, "3 years imprisonment / fine / both", 8),

        BnsEntry("194(1)", "Affray", "Fighting in a public place to the terror of the public.", SeverityLevel.LOW, true, true, "1 month / ₹1,000 / both", 3),
        BnsEntry("194(2)", "Affray", "Fighting in a public place to the terror of the public.", SeverityLevel.LOW, true, true, "1 month / ₹1,000 / both", 3),

        BnsEntry("196", "Promoting Enmity between Groups", "Promoting enmity between different religious, racial, or linguistic groups.", SeverityLevel.HIGH, true, false, "3 years imprisonment / fine / both", 7),



        // ── Chapter XII: Offences by or Related To Public Servants ───────────────────────
        BnsEntry("198", "PS Disobeying Law", "Public servant disobeying law with intent to cause injury to any person.", SeverityLevel.MEDIUM, false, true, "1 year or fine or both", 4),
        BnsEntry("201", "PS Framing Incorrect Document", "Public servant framing an incorrect document with intent to cause injury.", SeverityLevel.MEDIUM, true, true, "3 years or fine or both", 5),





        // ── Chapter XIII CONTEMPTS OF THE LAWFUL AUTHORITY OF PUBLIC SERVANTS ─────────────────

        BnsEntry("214", "Refusing to Answer Public Servant", "Refusing to answer a public servant authorized to question regarding a subject.", SeverityLevel.LOW, false, true, "6 months or ₹5,000 fine or both", 3),
        BnsEntry("217", "False Info to Injure Another", "Giving false information with intent to cause a public servant to use lawful power to injury of another.", SeverityLevel.MEDIUM, true, true, "1 year or fine or both", 5),
        BnsEntry("221", "Obstructing Public Functions", "Voluntarily obstructing a public servant in the discharge of public functions.", SeverityLevel.MEDIUM, true, true, "4 months or ₹5,000 fine or both", 5),
        BnsEntry("223", "Disobedience to Order", "Disobedience to an order duly promulgated by a public servant (formerly IPC 188).", SeverityLevel.LOW, true, true, "6 months or ₹2,500 fine", 3),
        BnsEntry("224", "Threat of Injury to Public Servant", "Holding out threat of injury to a public servant to induce them to do or forbear an act.", SeverityLevel.MEDIUM, true, true, "2 years or fine or both", 6),
        BnsEntry("225", "Threat to Refrain from Protection", "Threat of injury to induce a person to refrain from applying for protection to a public servant.", SeverityLevel.MEDIUM, true, true, "1 year or fine or both", 5),
        BnsEntry("226", "Suicide Attempt to Deter", "Attempting suicide to compel or restrain a public servant from exercising lawful power.", SeverityLevel.MEDIUM, true, true, "1 year or community service", 4),





        // ── Chapter XIV: FALSE EVIDENCE AND OFFENCES AGAINST PUBLIC JUSTICE──────────────────────────────

        BnsEntry("227", "Giving False Evidence", "Making a false statement under oath or bound by law to state the truth.", SeverityLevel.MEDIUM, true, true, "As per judicial proceeding.", 5),
        BnsEntry("229", "Giving/Fabricating False Evidence", "Intentionally giving false evidence or fabricating false evidence for use in judicial proceedings.", SeverityLevel.HIGH, true, false, "7 years + fine.", 7),
        BnsEntry("229(2)", "Punishment for Giving False Evidence", "Punishment for giving false evidence in any stage of a judicial proceeding.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 7),
        BnsEntry("229(3)", "False Evidence to Procure Capital Conviction", "Giving false evidence with intent to procure conviction for a capital offence.", SeverityLevel.CRITICAL, true, false, "Death or life imprisonment", 9),
        BnsEntry("230", "False Evidence for Capital Conviction", "Giving false evidence with intent to procure conviction of a capital offence.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine.", 10),
        BnsEntry("231", "Fabricating Evidence for Serious Offense", "Giving or fabricating false evidence with intent to procure conviction of an offence punishable with imprisonment for 7 years or upwards.", SeverityLevel.HIGH, true,false,"Same as the punishment for the offence intended to be convicted.", 8),
        BnsEntry("232", "Threatening to Give False Evidence", "Threatening any person to give false evidence in a case.", SeverityLevel.HIGH, true, false, "7 years + fine.", 7),
        BnsEntry("233", "Using Evidence Known to be False", "Corruptly using as true evidence which is known to be false or fabricated.", SeverityLevel.HIGH, true, false, "Same as giving false evidence.", 7),
        BnsEntry("234", "Issuing/Signing False Certificate", "Issuing or signing a certificate required by law knowing it is false in a material point.", SeverityLevel.MEDIUM, true, true, "Same as giving false evidence.", 5),
        BnsEntry("235", "Using False Certificate as True", "Corruptly using a false certificate as a true certificate.", SeverityLevel.MEDIUM, true, true, "Same as giving false evidence.", 5),
        BnsEntry("236", "False Statement in Declaration", "Making a false statement in a declaration receivable as evidence by law.", SeverityLevel.MEDIUM, true, true, "Same as giving false evidence.", 5),
        BnsEntry("237", "Using False Declaration as True", "Corruptly using a false declaration as true knowing it is false.", SeverityLevel.MEDIUM, true, true, "Same as giving false evidence.", 5),
        BnsEntry("238", "Disappearance of Evidence", "Screening offender by causing disappearance of evidence.", SeverityLevel.HIGH, true, false, "3 to 7 years + fine", 7),
        BnsEntry("246", "False Claim in Court", "Fraudulently or dishonestly making a false claim in a Court with intent to injure or annoy.", SeverityLevel.MEDIUM, false, true, "2 years + fine.", 4),
        BnsEntry("248", "False Charge with Intent to Injure", "Instituting or causing criminal proceedings against a person knowing charges to be false.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 7),
        BnsEntry("249", "Harbouring Offender", "Providing shelter or assistance to a person known to have committed an offence.", SeverityLevel.MEDIUM, true, true, "As per offence harboured", 5),
        BnsEntry("252", "Taking Gift for Stolen Property", "Taking reward for helping to recover stolen property without using efforts to apprehend the offender.", SeverityLevel.MEDIUM, true, true, "2 years or fine or both.", 5),
        BnsEntry("253", "Harbouring Escaped Offender", "Harbouring an offender who has escaped from custody or whose apprehension has been ordered.", SeverityLevel.MEDIUM, true, true, "7 years + fine (varies).", 6),
        BnsEntry("254", "Harbouring Robbers/Dacoits", "Knowingly harbouring persons who are about to commit or have committed robbery or dacoity.", SeverityLevel.HIGH, true, false, "7 years rigorous imprisonment + fine.", 7),
        BnsEntry("257", "PS Making Corrupt Report", "Public servant in a judicial proceeding corruptly making a report or order contrary to law.", SeverityLevel.HIGH, true, false, "7 years + fine.", 7),
        BnsEntry("258", "Corrupt Commitment for Trial", "Person with authority corruptly committing a person for trial or confinement knowing they act contrary to law.", SeverityLevel.HIGH, true, false, "7 years + fine.", 7),
        BnsEntry("259", "PS Omission to Apprehend", "Public servant intentionally omitting to apprehend a person they are bound to apprehend.", SeverityLevel.MEDIUM, true, true, "7 years + fine (varies).", 6),
        BnsEntry("260", "PS Omission (Sentenced Person)", "Public servant omitting to apprehend a person under sentence or lawfully committed.", SeverityLevel.HIGH, true, false, "Life or 14 years (varies).", 8),
        BnsEntry("263", "Resistance to Apprehension of Another", "Resistance or obstruction to the lawful apprehension of another person.", SeverityLevel.MEDIUM, true, true, "As per judicial order.", 5),

        // ── Chapter XV: Public Health & Safety ──────────────────────────────
        BnsEntry("271", "Spread of Infection", "Negligent act likely to spread dangerous disease.", SeverityLevel.MEDIUM, true, true, "6 months or fine", 4),
        BnsEntry("281", "Rash Driving", "Driving/riding on public way in rash/negligent manner.", SeverityLevel.MEDIUM, true, true, "6 months or fine", 5),
        BnsEntry("283", "Exhibition of False Light or Mark", "Whoever displays a false light, mark, or buoy, knowing or intending it to mislead any navigator.", SeverityLevel.HIGH, true, false, "Imprisonment of 7 years or fine (minimum ₹10,000) or both.", 7),
        BnsEntry("285", "Obstruction in Public Way", "Causing danger or obstruction in public navigation.", SeverityLevel.LOW, false, true, "₹5,000 fine", 3),


        // ── Chapter XVI: Offences Relating to Religion ─────────────────────
        BnsEntry("298", "Defiling Place of Worship", "Injuring or defiling place of worship with intent to insult religion.", SeverityLevel.HIGH, true, false, "2 years or fine or both", 7),
        BnsEntry("299", "Outraging Religious Feelings", "Deliberate acts intended to outrage religious feelings of any class.", SeverityLevel.HIGH, true, false, "3 years or fine or both", 7),
        BnsEntry("302", "Uttering Words to Wound Religious Feelings", "Uttering words etc., with deliberate intent to wound religious feelings.", SeverityLevel.LOW, false, true, "1 year or fine or both", 3),

        // ── Chapter XVII: Property Offences ──────────────────────────────────

        BnsEntry("303(1)", "Theft", "Dishonestly taking movable property out of the possession of another without consent.", SeverityLevel.MEDIUM, true, true, "3 years imprisonment / fine / both", 5),
        BnsEntry("303(2)", "Theft", "Dishonest taking of movable property.", SeverityLevel.MEDIUM, true, true, "3 years + fine", 5),
        BnsEntry("304", "Snatching", "Theft by sudden/forceful grabbing (New BNS Category).", SeverityLevel.MEDIUM, true, false, "3 years + fine", 6),
        BnsEntry("305", "Theft in Dwelling", "Theft in house, vessel, or place of worship.", SeverityLevel.HIGH, true, false, "7 years + fine", 7),
        BnsEntry("305(a)", "Theft in Dwelling House", "Theft committed in a dwelling house or vessel used as a dwelling.", SeverityLevel.MEDIUM, true, false, "7 years imprisonment + fine", 6),
        BnsEntry("305(b)", "Theft after Preparation for Hurt", "Theft committed after making preparation to cause hurt or wrongful restraint.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 7),
        BnsEntry("306", "Theft by Clerk or Servant", "Theft by a clerk or servant of property in possession of their master.", SeverityLevel.HIGH, true, false, "7 years + fine.", 7),
        BnsEntry("307", "Theft after Prep for Hurt", "Theft committed after making preparation for causing death, hurt, or restraint.", SeverityLevel.HIGH, true, false, "10 years rigorous imprisonment + fine.", 8),
        BnsEntry("308(1)", "Extortion", "Putting a person in fear of injury or death to dishonestly induce delivery of property.", SeverityLevel.HIGH, true, false, "3 years imprisonment / fine / both", 7),
        BnsEntry("308(2)", "Extortion", "Inducing delivery of property by putting in fear.", SeverityLevel.HIGH, true, false, "7 years + fine", 7),
        BnsEntry("308(4)", "Extortion by Threat of Death", "Extortion by putting a person in fear of death or grievous hurt.", SeverityLevel.HIGH, true, false, "10 years imprisonment + fine", 8),
        BnsEntry("309(1)", "Robbery", "Theft or extortion where force or threat of force is used.", SeverityLevel.HIGH, true, false, "10 years imprisonment + fine", 8),
        BnsEntry("309(2)", "Robbery", "Rigorous imprisonment for the offence of robbery.", SeverityLevel.HIGH, true, false, "10 years imprisonment + fine", 8),
        BnsEntry("309(4)", "Robbery with Hurt", "Hurt caused while committing robbery.", SeverityLevel.CRITICAL, true, false, "Life or 10 years", 9),
        BnsEntry("310(1)", "Dacoity", "Robbery committed by five or more persons conjointly.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 9),
        BnsEntry("310(2)", "Dacoity", "Robbery by five or more persons.", SeverityLevel.CRITICAL, true, false, "10 years to Life", 9),
        BnsEntry("311", "Dacoity with Murder", "Murder committed by a gang while committing dacoity.", SeverityLevel.CRITICAL, true, false, "Death or life imprisonment + fine", 10),
        BnsEntry("312", "Attempted Robbery with Weapon", "Attempting to commit robbery or dacoity when armed with a deadly weapon.", SeverityLevel.HIGH, true, false, "Minimum 7 years imprisonment.", 8),
        BnsEntry("313", "Belonging to Gang of Robbers", "Punishment for belonging to a gang of robbers or habitual thieves.", SeverityLevel.HIGH, true, false, "7 years rigorous imprisonment + fine.", 7),
        BnsEntry("315", "Misappropriation of Deceased's Property", "Dishonest misappropriation of property possessed by a deceased person at death.", SeverityLevel.MEDIUM, false, true, "3 years + fine.", 6),
        BnsEntry("316(1)", "Criminal Breach of Trust", "Dishonest misappropriation of property entrusted to someone.", SeverityLevel.HIGH, true, false, "3 years imprisonment / fine / both", 7),
        BnsEntry("316(2)", "Punishment for Criminal Breach of Trust", "Imprisonment and fine for criminal breach of trust.", SeverityLevel.HIGH, true, false, "3 years imprisonment / fine", 7),
        // ── Chapter XVII: Property Offences ──────────────────────────────────
        BnsEntry("317(1)", "Stolen Property (Definition)", "Property transferred by theft, extortion, robbery, cheating, misappropriation, or breach of trust.", SeverityLevel.MEDIUM, true, true, "N/A (Definition Section)", 4),
        BnsEntry("317(2)", "Receiving Stolen Property", "Dishonestly receiving or retaining stolen property knowing or having reason to believe it is stolen.", SeverityLevel.HIGH, true, true, "3 years or fine or both", 7),
        BnsEntry("317(3)", "Stolen Property from Dacoity", "Receiving stolen property transferred by dacoity or from a gang of dacoits.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years rigorous + fine", 10),
        BnsEntry("317(4)", "Habitual Dealing in Stolen Property", "Habitually receiving or dealing in property known or believed to be stolen.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 9),
        BnsEntry("317(5)", "Assisting in Concealment", "Voluntarily assisting in concealing, disposing of, or making away with stolen property.", SeverityLevel.MEDIUM, true, true, "3 years or fine or both", 6),

        BnsEntry("318(1)", "Cheating (Definition)", "Deceiving any person to deliver property or to consent that any person shall retain property.", SeverityLevel.MEDIUM, true, true, "N/A (Definition Section)", 4),
        BnsEntry("318(2)", "Punishment for Cheating", "Standard punishment for simple cheating.", SeverityLevel.MEDIUM, true, true, "3 years or fine or both", 6),
        BnsEntry("318(3)", "Cheating with Knowledge of Loss", "Cheating with knowledge that wrongful loss may occur to a person whose interest the offender is bound to protect.", SeverityLevel.HIGH, true, true, "5 years or fine or both", 7),
        BnsEntry("318(4)", "Cheating and Dishonestly Inducing Delivery", "Cheating and thereby dishonestly inducing the person deceived to deliver any property (Formerly IPC 420).", SeverityLevel.HIGH, true, false, "7 years + fine", 8),

        BnsEntry("319(1)", "Cheating by Personation (Definition)", "Cheating by pretending to be some other person.", SeverityLevel.MEDIUM, true, false, "N/A (Definition Section)", 4),
        BnsEntry("319(2)", "Punishment for Cheating by Personation", "Punishment for the offense of cheating by personation.", SeverityLevel.HIGH, true, false, "5 years or fine or both", 7),

        BnsEntry("324(1)", "Mischief", "Causing destruction or damage to property with intent to cause wrongful loss.", SeverityLevel.LOW, true, true, "3 months / ₹5,000 / both", 3),
        BnsEntry("324(2)", "Mischief", "Causing destruction or damage to property.", SeverityLevel.LOW, true, true, "6 months or fine", 3),
        BnsEntry("329(1)", "Criminal Trespass", "Entering or remaining on another's property with intent to commit an offence.", SeverityLevel.LOW, false, true, "3 months / ₹5,000 / both", 3),
        BnsEntry("329(2)", "House Trespass", "Criminal trespass into a building used as a dwelling place.", SeverityLevel.MEDIUM, false, true, "1 year / fine / both", 4),
        BnsEntry("329(3)", "Criminal Trespass", "Entering property to commit an offence.", SeverityLevel.LOW, false, true, "3 months or ₹5,000 fine", 3),
        BnsEntry("329(4)", "Punishment for House Trespass", "Punishment for house trespass to commit an offence.", SeverityLevel.MEDIUM, false, true, "1 year / fine / both", 4),
        BnsEntry("330(1)", "Lurking House-Trespass", "Committing house-trespass having taken precautions to conceal such trespass.", SeverityLevel.MEDIUM, true, true, "Same as house-trespass.", 4),
        BnsEntry("330(2)", "House-Breaking", "Committing house-trespass by entering or quitting the house in specific forceful ways.", SeverityLevel.MEDIUM, true, false, "Same as house-trespass.", 5),
        BnsEntry("331(1)", "Lurking House-trespass / House-breaking", "Committing lurking house-trespass or house-breaking.", SeverityLevel.MEDIUM, true, true, "2 years + fine", 6),
        BnsEntry("331(2)", "Lurking House-trespass / House-breaking by Night", "Committing the offence after sunset and before sunrise.", SeverityLevel.HIGH, true, false, "3 years + fine", 7),
        BnsEntry("331(3)", "House-breaking with Intent to Offend", "Committing the offence to commit another crime; if intended offence is theft, punishment increases.", SeverityLevel.HIGH, true, false, "3 years (General) / 10 years (Theft) + fine", 8),
        BnsEntry("331(4)", "House-breaking by Night with Intent to Offend", "Night-time offence to commit another crime; if intended offence is theft, punishment increases.", SeverityLevel.CRITICAL, true, false, "5 years (General) / 14 years (Theft) + fine", 9),
        BnsEntry("331(5)", "House-breaking with Prep for Hurt", "House-breaking after making preparation to cause hurt, assault, or wrongful restraint.", SeverityLevel.CRITICAL, true, false, "10 years + fine", 9),
        BnsEntry("331(6)", "House-breaking by Night with Prep for Hurt", "Night-time house-breaking after making preparation for hurt or assault.", SeverityLevel.CRITICAL, true, false, "14 years + fine", 10),
        BnsEntry("331(7)", "Grievous Hurt during House-breaking", "Causing or attempting to cause death or grievous hurt while committing the offence.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 10),
        BnsEntry("331(8)", "Joint Liability for Death/Hurt at Night", "Every person jointly concerned is liable if death or grievous hurt is caused during night-time house-breaking.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 10 years + fine", 10),
        BnsEntry("333", "House-Trespass with Preparation for Hurt", "Committing house-trespass after making preparation for causing hurt, assault, or wrongful restraint.", SeverityLevel.HIGH, true, false, "7 years + fine.", 7),

        // ── Chapter XVIII/XIX: Forgery & Defamation ──────────────────────────
        BnsEntry("336(1)", "Forgery","Making a false document or electronic record with intent to cause damage or fraud.", SeverityLevel.HIGH, true, false, "2 years imprisonment / fine / both", 7),
        BnsEntry("336(2)", "Making False Document", "Creating a false document knowing it to be false for fraudulent purpose.", SeverityLevel.HIGH, true, false, "2 years imprisonment / fine / both", 7),
        BnsEntry("336(3)", "Forgery", "Punishment for making false documents.", SeverityLevel.HIGH, true, false, "2 years + fine", 7),
        BnsEntry("337", "Forgery of Court Record", "Forging a document which purports to be a record or proceeding of a court.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 8),
        BnsEntry("338", "Forgery of Valuable Security", "Forging a will, power of attorney, cheque, bill of exchange, or promissory note.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 8),
        BnsEntry("339", "Forgery for Cheating", "Forgery committed with intention of using the forged document for cheating.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 8),
        BnsEntry("340(1)", "Forgery to Harm Reputation", "Forgery committed with intent to harm reputation of any party.", SeverityLevel.HIGH, true, false, "3 years imprisonment / fine / both", 7),
        BnsEntry("340(2)", "Forged Document — possession/use", "Using or possessing a forged document knowing it to be forged.", SeverityLevel.HIGH, true, false, "7 years imprisonment + fine", 8),
        BnsEntry("341(1)", "Counterfeit Seal for Serious Forgery", "Making or possessing counterfeit seals/instruments with intent to commit forgery punishable under section 338.", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 7 years + fine", 9),
        BnsEntry("341(2)", "Counterfeit Seal for General Forgery", "Making or possessing counterfeit seals/instruments with intent to commit any other forgery under this Chapter.", SeverityLevel.HIGH, true, false, "7 years + fine", 8),
        BnsEntry("341(3)", "Possession of Counterfeit Seal", "Possessing any seal, plate, or instrument knowing the same to be counterfeit.", SeverityLevel.MEDIUM, true, true, "3 years + fine", 6),
        BnsEntry("341(4)", "Using Counterfeit Seal as Genuine", "Fraudulently or dishonestly using a counterfeit seal or instrument as genuine.", SeverityLevel.HIGH, true, false, "Same as if made or counterfeited", 8),
        BnsEntry("342(1)", "Counterfeiting Auth Mark (Serious Docs)", "Counterfeiting a device or mark used to authenticate documents described in section 338 (wills, valuable securities, etc.).", SeverityLevel.CRITICAL, true, false, "Life imprisonment or 7 years + fine", 9),
        BnsEntry("342(2)", "Counterfeiting Auth Mark (General Docs/Records)", "Counterfeiting a device or mark used to authenticate any document or electronic record other than those in section 338.", SeverityLevel.HIGH, true, false, "7 years + fine", 8),
        BnsEntry("343", "Fraudulent Destruction of Will", "Fraudulent cancellation or destruction of a will, authority to adopt, or valuable security.", SeverityLevel.HIGH, true, false, "Life imprisonment or 7 years + fine.", 8),
        BnsEntry("344", "Falsification of Accounts", "Wilfully and with intent to defraud, destroying, altering, or falsifying accounts.", SeverityLevel.HIGH, true, false, "7 years + fine.", 7),
        BnsEntry("351(3)", "Criminal Intimidation", "Threatening another with injury.", SeverityLevel.MEDIUM, true, false, "2 years + fine", 5),
        BnsEntry("351(2)", "Criminal Intimidation", "Threatening another with injury.", SeverityLevel.MEDIUM, true, false, "2 years + fine", 5),
        BnsEntry("355", "Misconduct by Drunken Person", "Misconduct in public by a drunken person.", SeverityLevel.LOW, false, true, "24 hours or ₹1,000 fine", 1),
        BnsEntry("356(2)", "Defamation", "Harming reputation by words/signs.", SeverityLevel.LOW, false, true, "2 years or comm. service", 3),

        )





    private val bySection: Map<String, BnsEntry> = entries.associateBy { it.section }
    private val byBaseSection: Map<String, BnsEntry> = entries.associateBy { it.section.substringBefore("(") }

    fun find(section: String): BnsEntry? {

        val clean = section
            .replace(Regex("(?i)(BNS|IPC|u/s|section|sec\\.|s\\.)"), "") // Remove common prefixes
            .replace("\\s".toRegex(), "")                                // Remove all spaces
            .uppercase()
        android.util.Log.d("SeverityDB", "Searching for: '$clean'")
        val result = bySection[clean] ?: byBaseSection[clean.substringBefore("(")]
        if (result != null) {
            android.util.Log.d("SeverityDB", "✅ Match Found: ${result.section} (${result.shortTitle})")
        } else {
            android.util.Log.e("SeverityDB", "❌ No Match for: '$clean' (Base was: '${clean.substringBefore("(")}')")
            // LOG 3: Print a few keys from the DB to check formatting
            android.util.Log.d("SeverityDB", "Available keys sample: ${bySection.keys.take(5)}")
        }

        return result

    }

    fun findAll(sections: List<String>): Pair<List<BnsEntry>, List<String>> {
        val matched = mutableListOf<BnsEntry>()
        val unmatched = mutableListOf<String>()

        android.util.Log.d("SeverityDB", "--- findAll START ---")
        android.util.Log.d("SeverityDB", "Input sections: $sections") // See the raw list

        sections.forEach { sec ->
            val entry = find(sec)
            if (entry != null) {
                android.util.Log.d("SeverityDB", "MATCHED: '$sec' -> BNS ${entry.section}")
                matched.add(entry)
            } else {
                android.util.Log.e("SeverityDB", "UNMATCHED: '$sec' (find() returned null)")
                unmatched.add(sec)
            }
        }

        android.util.Log.d("SeverityDB", "Final results - Matched: ${matched.size}, Unmatched: ${unmatched.size}")
        android.util.Log.d("SeverityDB", "--- findAll END ---")

        return Pair(matched, unmatched)
    }

    fun computeSeverity(sections: List<String>): SeverityResult {
        if (sections.isEmpty()) return SeverityResult(SeverityLevel.LOW, false, true, 1, emptyList(), emptyList())
        val (matched, unmatched) = findAll(sections)
        if (matched.isEmpty()) return SeverityResult(SeverityLevel.MEDIUM, true, true, 5, emptyList(), unmatched)

        val sorted = matched.sortedByDescending { it.urgencyPoints }
        var score = sorted.first().urgencyPoints.toFloat()
        score += sorted.drop(1).count { it.urgencyPoints >= 7 } * 0.5f
        if (sections.any { it.startsWith("61") }) score += 1.0f

        val finalScore = score.coerceIn(1f, 10f).toInt()
        val level = when (finalScore) {
            in 1..3 -> SeverityLevel.LOW
            in 4..5 -> SeverityLevel.MEDIUM
            in 6..7 -> SeverityLevel.HIGH
            else -> SeverityLevel.CRITICAL
        }
        return SeverityResult(level, matched.any { it.cognisable }, matched.all { it.bailable }, finalScore, matched, unmatched)
    }

    fun levelName(level: SeverityLevel) = level.name.lowercase()
}
