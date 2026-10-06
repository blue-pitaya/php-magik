<?php

function nothing()
{
    echo 'x';
}

function early($a)
{
    if ($a) {
        return;
    }
}

function nested()
{
    $f = function () {
        return 1;
    };
}
