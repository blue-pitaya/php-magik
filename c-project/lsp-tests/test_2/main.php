<?php

function add()
{
    return 1 + 2 + 3 + 4 + 5;
}

function add2()
{
    return 1 + (2 + 3) * (4 + 5) + (1);
}

function add3()
{
    return ((1 + 2) * (3 * (3 + 4) / 5)) * 2;
}
