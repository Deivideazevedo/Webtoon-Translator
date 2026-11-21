#!/usr/bin/env python3
"""Abre uma página num WebKit2GTK 4.1 (o mesmo motor do Tauri no Linux), pra que o
playbackRate soe exatamente como soaria dentro do app. Uso: voice-speed-ab.py <index.html>"""
import sys
import gi

gi.require_version("Gtk", "3.0")
gi.require_version("WebKit2", "4.1")
from gi.repository import Gtk, WebKit2  # noqa: E402

win = Gtk.Window(title=sys.argv[2] if len(sys.argv) > 2 else "Comparativo de voz")
win.set_default_size(1040, 720)
win.connect("destroy", Gtk.main_quit)
view = WebKit2.WebView()
settings = view.get_settings()
settings.set_allow_file_access_from_file_urls(True)
settings.set_media_playback_requires_user_gesture(False)
view.load_uri("file://" + sys.argv[1])
win.add(view)
win.show_all()
Gtk.main()
