from pathlib import Path

home = Path("app/src/main/java/com/yourname/pdftoolkit/ui/screens/PremiumToolsScreen.kt")
text = home.read_text(encoding="utf-8")

if "import androidx.compose.foundation.lazy.grid.GridItemSpan" not in text:
    text = text.replace(
        "import androidx.compose.foundation.lazy.grid.GridCells\n",
        "import androidx.compose.foundation.lazy.grid.GridCells\n"
        "import androidx.compose.foundation.lazy.grid.GridItemSpan\n",
        1,
    )

start_anchor = (
    "            Column(\n"
    "                modifier = Modifier\n"
    "                    .fillMaxSize()\n"
    "                    .padding(horizontal = if (expanded) 28.dp else 16.dp),\n"
    "            ) {"
)
end_anchor = (
    "\n            }\n"
    "        }\n"
    "    }\n"
    "}\n\n"
    "@Composable\n"
    "private fun WorkspaceHero"
)

start = text.index(start_anchor)
end = text.index(end_anchor, start)

replacement = '''            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (expanded) 236.dp else 172.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (expanded) 28.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 12.dp),
                contentPadding = PaddingValues(bottom = 34.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "workspace_hero") {
                    WorkspaceHero(
                        toolCount = resolvedTools.size,
                        expanded = expanded,
                        onOpenPdf = { openPdf.launch(arrayOf("application/pdf")) },
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }, key = "tool_search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Search all PDF tools") },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                    )
                }

                if (!expanded) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "tool_filters") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSection == null,
                                    onClick = { selectedSection = null },
                                    label = { Text("All tools") },
                                )
                            }
                            items(premiumSections, key = { it.name }) { section ->
                                FilterChip(
                                    selected = selectedSection == section,
                                    onClick = { selectedSection = section },
                                    label = { Text(sectionLabel(section)) },
                                )
                            }
                        }
                    }
                }

                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "section_heading_${selectedSection?.name ?: "all"}",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = sectionLabel(selectedSection),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Choose a tool and work completely offline",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "${visibleTools.size} tools",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            )
                        }
                    }
                }

                items(visibleTools, key = { it.source.id }) { tool ->
                    PremiumToolCard(
                        tool = tool,
                        expanded = expanded,
                        onClick = { openTool(tool.source) },
                    )
                }
            }'''

text = text[:start] + replacement + text[end:]
home.write_text(text, encoding="utf-8")

nav = Path("app/src/main/java/com/yourname/pdftoolkit/ui/navigation/AppNavigation.kt")
text = nav.read_text(encoding="utf-8")

if "import androidx.compose.ui.input.nestedscroll.nestedScroll" not in text:
    text = text.replace(
        "import androidx.compose.ui.Modifier\n",
        "import androidx.compose.ui.Modifier\n"
        "import androidx.compose.ui.input.nestedscroll.nestedScroll\n",
        1,
    )

show_top_bar_marker = '''    val showTopBar = currentRoute in listOf(
        Screen.Tools.route,
        Screen.Files.route
    )
'''
scroll_behavior_block = '''
    // Collapse while the user scrolls toward later content. Reveal as soon as
    // scrolling reverses so navigation never feels lost.
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(currentRoute) {
        topBarScrollBehavior.state.heightOffset = 0f
        topBarScrollBehavior.state.contentOffset = 0f
    }
'''

if "val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()" not in text:
    if show_top_bar_marker not in text:
        raise SystemExit("showTopBar marker not found")
    text = text.replace(
        show_top_bar_marker,
        show_top_bar_marker + scroll_behavior_block,
        1,
    )

scaffold_old = "    Scaffold(\n        modifier = Modifier.fillMaxSize(),"
scaffold_new = (
    "    Scaffold(\n"
    "        modifier = Modifier\n"
    "            .fillMaxSize()\n"
    "            .nestedScroll(topBarScrollBehavior.nestedScrollConnection),"
)
if scaffold_old in text:
    text = text.replace(scaffold_old, scaffold_new, 1)

app_bar_old = '''                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
'''
app_bar_new = '''                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    scrollBehavior = topBarScrollBehavior,
                )
'''
if app_bar_old in text:
    text = text.replace(app_bar_old, app_bar_new, 1)

nav.write_text(text, encoding="utf-8")
