<?php

class Box
{
    public int $value;

    public function get()
    {
        return $this->value;
    }
}

function main()
{
    $b = new Box;
    $x = 10;
}
