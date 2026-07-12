<?php

function zero()
{
    return 0;
}

function add(int $a, int $b)
{
    return $a + $b;
}

function addf(float $a, float $b)
{
    return $a + $b;
}

function combined()
{
    $a = zero() + 5;
    $b = 10;
    $c = add($a, $b);

    return $c;
}
