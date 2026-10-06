<?php

class Outer
{
    public $outer;

    public function make()
    {
        class Inner
        {
            public $inner;
        }
    }
}
