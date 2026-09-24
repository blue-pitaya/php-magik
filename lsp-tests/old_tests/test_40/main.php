<?php

/**
 * Adds two numbers.
 *
 * @param int $a
 * @return int
 */
function add()
{
    return 1 + 2;
}

/* not a doc block */
function plain()
{
    return 0;
}

function undocumented()
{
    return 0;
}

class Calc
{
    /**
     * Doubles a value.
     */
    public function double()
    {
        return 2 * 2;
    }
}

$sum = add();
