package com.example.mementoandroid.ui.home

sealed class HomeAddAction {
    object Camera : HomeAddAction()
    object Photos : HomeAddAction()
    object MakeAlbum : HomeAddAction()
}