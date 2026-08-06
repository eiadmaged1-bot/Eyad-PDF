from pathlib import Path

path = Path("app/src/main/java/com/yourname/pdftoolkit/ui/navigation/AppNavigation.kt")
text = path.read_text(encoding="utf-8")

if "import androidx.compose.animation.EnterTransition" not in text:
    text = text.replace(
        "import androidx.compose.animation.AnimatedVisibility\n",
        "import androidx.compose.animation.AnimatedVisibility\n"
        "import androidx.compose.animation.EnterTransition\n"
        "import androidx.compose.animation.ExitTransition\n",
        1,
    )

old = (
    "        NavHost(\n"
    "            navController = navController,\n"
    "            startDestination = actualStartDestination,\n"
    "            modifier = Modifier\n"
    "                .fillMaxSize()\n"
    "                .padding(paddingValues)\n"
    "        ) {\n"
)
new = (
    "        NavHost(\n"
    "            navController = navController,\n"
    "            startDestination = actualStartDestination,\n"
    "            modifier = Modifier\n"
    "                .fillMaxSize()\n"
    "                .padding(paddingValues),\n"
    "            enterTransition = { EnterTransition.None },\n"
    "            exitTransition = { ExitTransition.None },\n"
    "            popEnterTransition = { EnterTransition.None },\n"
    "            popExitTransition = { ExitTransition.None },\n"
    "        ) {\n"
)

if old not in text:
    raise SystemExit("NavHost transition anchor not found")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
