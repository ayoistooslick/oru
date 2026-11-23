"""Layouts that name classes are only checked at runtime, on a phone.

A wrong class name compiles and packages fine, then throws InflateException the
moment the screen opens. The signed-in screen shipped exactly that once, so the
fragment host and its library are pinned here.
"""

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
LAYOUTS = ROOT / "android" / "app" / "src" / "main" / "res" / "layout"
MAIN = LAYOUTS / "activity_main.xml"
GRADLE = ROOT / "android" / "app" / "build.gradle"

# The class that actually exists in androidx.navigation:navigation-fragment.
NAV_HOST = "androidx.navigation.fragment.NavHostFragment"


def layout_names():
    """Every android:name="..." class reference in every layout."""
    found = []
    for path in LAYOUTS.glob("*.xml"):
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r'android:name="([^"]+)"', text):
            found.append((path.name, match.group(1)))
    return found


def test_no_layout_names_a_missing_fragment_class():
    # androidx.fragment.app.NavHostFragment never existed: the host lives in
    # the navigation library. This is the typo that broke sign-in.
    wrong = [
        (layout, name)
        for layout, name in layout_names()
        if name == "androidx.fragment.app.NavHostFragment"
    ]
    assert not wrong, f"layouts name a class that does not exist: {wrong}"


def test_main_screen_hosts_the_graph_with_the_navigation_class():
    main = MAIN.read_text(encoding="utf-8")
    assert 'android:id="@+id/nav_host_fragment"' in main
    assert f'android:name="{NAV_HOST}"' in main
    assert 'app:navGraph="@navigation/nav_graph"' in main


def test_navigation_fragment_library_is_declared():
    # Without this dependency the class is not on the runtime classpath, and the
    # same exception comes back with the opposite cause.
    assert "androidx.navigation:navigation-fragment" in GRADLE.read_text(
        encoding="utf-8")
